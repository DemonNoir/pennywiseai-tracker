package com.pennywiseai.tracker.slip.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.pennywiseai.tracker.slip.parser.ParsedSlip
import com.pennywiseai.tracker.slip.parser.SlipParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Line detection result containing bounding box and text content.
 */
data class DetectedLine(
    val boundingBox: Rect,
    val text: String
)

/**
 * Hybrid on-device OCR engine for Thai + English bank slips.
 *
 * Pipeline (see OCR_MEASUREMENT_REPORT.md for the measurement that drove this):
 *  1. Tesseract 5 (tha+eng) is the PRIMARY engine — it reads Thai glyphs and
 *     amounts far more accurately than PaddleOCR (7/8 amounts vs 5/8, and it
 *     keeps receiver/direction text intact).
 *  2. PaddleOCR is kept only as a fallback for the bank LOGO in the header
 *     (Tesseract misreads "SCB" as "020 0 568:") and as a last-resort OCR when
 *     Tesseract is unavailable/empty.
 *
 * Processed 100% locally on-device without network transmission.
 */
@Singleton
class SlipOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tesseractEngine: TesseractOcrEngine
) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var dictionaryList: List<String> = emptyList()
    private val engineReadyDeferred = CompletableDeferred<Unit>()
    private var isModelLoaded = false

    init {
        ioScope.launch {
            initOnnxModels()
            engineReadyDeferred.complete(Unit)
        }
    }

    /**
     * Waits until the OCR models and Tesseract engine are fully initialized.
     * Use this in background workers to avoid scanning before the engine is ready.
     */
    suspend fun waitForReady() {
        engineReadyDeferred.await()
        tesseractEngine.waitForReady()
    }

    private fun initOnnxModels() {
        try {
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env

            val detBytes = loadAssetBytes("models/det/inference.onnx")
            val recBytes = loadAssetBytes("models/rec/inference.onnx")
            val dictString = loadAssetString("models/rec/keys.txt")

            if (detBytes != null && recBytes != null && dictString != null) {
                detSession = env.createSession(detBytes)
                recSession = env.createSession(recBytes)
                // Trim only CR/LF, NOT general whitespace: the last dict line is a
                // literal space character (PaddleOCR use_space_char) and .trim()
                // would silently strip it, dropping every space from decoded text.
                dictionaryList = dictString.lines().map { it.trim('\r', '\n') }
                isModelLoaded = true
                Log.i("SlipOcrEngine", "PaddleOCR ONNX models and dictionary loaded successfully.")
            } else {
                Log.w("SlipOcrEngine", "ONNX model assets missing in assets/models/. Ready for deployment.")
            }
        } catch (e: Exception) {
            Log.e("SlipOcrEngine", "Failed to initialize ONNX Runtime sessions: ${e.message}", e)
        }
    }

    /**
     * Process image URI asynchronously on Dispatchers.IO and return parsed slip via callback.
     */
    fun processImageUri(
        imageUri: Uri,
        onSuccess: (ParsedSlip) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ioScope.launch {
            try {
                val rawOcrText = getRawTextSync(imageUri)
                val parsedSlip = SlipParser.parse(rawOcrText).copy(imageUriString = imageUri.toString())

                Log.d("SlipOcrEngine", "OCR extracted ${rawOcrText.length} characters from image $imageUri")
                Log.d("SlipOcrEngine", "Parsed Result: Bank=${parsedSlip.bankName}, Amount=${parsedSlip.amount}, Confidence=${parsedSlip.confidence}")

                withContext(Dispatchers.Main) {
                    onSuccess(parsedSlip)
                }
            } catch (e: Exception) {
                Log.e("SlipOcrEngine", "OCR processing failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    /**
     * Parse raw text string directly without running OCR.
     */
    fun processRawText(rawText: String): ParsedSlip {
        return SlipParser.parse(rawText)
    }


    /**
     * Synchronous / Suspending raw OCR text extraction from an Image URI.
     * Executes on Dispatchers.IO thread.
     * Throws exception on failure so caller handles onError correctly.
     */
    suspend fun getRawTextSync(imageUri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(imageUri)
            ?: throw IllegalArgumentException("Could not load bitmap from URI: $imageUri")

        try {
            val startTime = System.currentTimeMillis()
            
            // Mask QR Codes to prevent OCR from hallucinating text on them
            val cleanedBitmap = QrMasker.maskQrCodes(bitmap)
            
            val extractedText = runHybridOcr(cleanedBitmap)
            
            if (cleanedBitmap != bitmap) {
                cleanedBitmap.recycle()
            }
            val elapsedTime = System.currentTimeMillis() - startTime

            Log.d("SlipOcrEngine", "OCR execution time for $imageUri: ${elapsedTime}ms")
            extractedText
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Hybrid OCR: Tesseract primary, Paddle for bank-logo fallback.
     * Runs on the calling (IO) thread.
     */
    private suspend fun runHybridOcr(bitmap: Bitmap): String {
        val tessText = tesseractEngine.recognize(bitmap)

        if (tesseractEngine.isAvailable && tessText.isNotBlank()) {
            // Tesseract read something — check if the bank is in its text.
            if (SlipParser.detectBankName(tessText) != null) {
                return tessText
            }
            // Bank missing (e.g. "SCB" logo misread as "020 0 568:") — Paddle
            // reads the logo reliably, so prepend the bank it detects from the
            // header region only (cheap, no full Paddle pass).
            val bank = detectBankFromPaddleHeader(bitmap)
            return if (bank != null) {
                "$bank\n$tessText"
            } else {
                tessText
            }
        }

        // Tesseract unavailable or produced nothing. 
        // We no longer fallback to full-image PaddleOCR to save processing time and memory.
        return tessText
    }

    /**
     * Detect the bank logo with Paddle by OCR'ing only the top header band of
     * the slip. The logo lives in the first ~18% of the image height, so this
     * is a much cheaper pass than running the full Paddle pipeline.
     */
    private fun detectBankFromPaddleHeader(bitmap: Bitmap): String? {
        if (!isModelLoaded || ortEnv == null || recSession == null) {
            return null
        }
        val headerH = (bitmap.height * 0.18f).toInt().coerceAtLeast(64)
        if (bitmap.height <= headerH || bitmap.width <= 64) return null

        val header = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, headerH)
        return try {
            val headerText = runOcrInternal(header)
            SlipParser.detectBankName(headerText)
        } catch (e: Exception) {
            Log.w("SlipOcrEngine", "Paddle bank-header detection failed: ${e.message}")
            null
        } finally {
            header.recycle()
        }
    }

    private fun runOcrInternal(bitmap: Bitmap): String {
        if (!isModelLoaded || ortEnv == null || recSession == null) {
            Log.d("SlipOcrEngine", "ONNX Models not active in assets/models/. Returning empty raw text.")
            return ""
        }

        val detectedLines = mutableListOf<DetectedLine>()
        try {
            val crops = detectTextRegions(bitmap)
            Log.d("SlipOcrEngine", "Detected ${crops.size} potential text regions in image.")

            for ((rect, croppedBitmap) in crops) {
                try {
                    val lineText = recognizeText(croppedBitmap)
                    if (lineText.isNotBlank()) {
                        detectedLines.add(DetectedLine(rect, lineText))
                    }
                } catch (e: Exception) {
                    Log.w("SlipOcrEngine", "Failed to recognize crop at $rect: ${e.message}")
                } finally {
                    croppedBitmap.recycle()
                }
            }

            // Step 3: Sort by top Y-position (top-to-bottom)
            val sortedLines = detectedLines.sortedBy { it.boundingBox.top }
            val fullText = sortedLines.joinToString("\n") { it.text }
            Log.d("SlipOcrEngine", "OCR internal completed. Lines: ${sortedLines.size}, Characters: ${fullText.length}")
            return fullText
        } catch (e: Exception) {
            Log.e("SlipOcrEngine", "Error executing runOcrInternal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Step 1: Detect text regions, post-process probability map, and return cropped bounding boxes.
     */
    private fun detectTextRegions(bitmap: Bitmap): List<Pair<Rect, Bitmap>> {
        val session = detSession ?: return fallbackFullImageCrop(bitmap)

        val maxDim = maxOf(bitmap.width, bitmap.height)
        val scale = if (maxDim > 960) 960.0f / maxDim else 1.0f
        var targetW = (bitmap.width * scale).toInt().coerceAtLeast(32)
        var targetH = (bitmap.height * scale).toInt().coerceAtLeast(32)

        targetW = (targetW + 31) / 32 * 32
        targetH = (targetH + 31) / 32 * 32

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val crops = mutableListOf<Pair<Rect, Bitmap>>()

        try {
            val pixels = IntArray(targetW * targetH)
            scaledBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

            val floatBuffer = FloatBuffer.allocate(1 * 3 * targetH * targetW)

            // ImageNet Mean & Std Normalization (NCHW)
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)

            for (c in 0 until 3) {
                val shift = (2 - c) * 8
                val m = mean[c]
                val s = std[c]
                for (y in 0 until targetH) {
                    for (x in 0 until targetW) {
                        val p = pixels[y * targetW + x]
                        val channelVal = (p shr shift and 0xFF) / 255.0f
                        floatBuffer.put((channelVal - m) / s)
                    }
                }
            }
            floatBuffer.rewind()

            val inputName = session.inputNames.iterator().next() ?: "x"
            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))

            session.run(mapOf(inputName to inputTensor)).use { results ->
                val outputTensor = results.get(0).value
                val probMap = extractProbabilityMap(outputTensor, targetH, targetW)

                // Simplified DB post-processing: slice horizontal bands thresholded > 0.3
                val threshold = 0.3f
                var startY = -1

                for (y in 0 until targetH) {
                    var rowMaxProb = 0.0f
                    for (x in 0 until targetW) {
                        if (probMap[y][x] > rowMaxProb) rowMaxProb = probMap[y][x]
                    }

                    if (rowMaxProb >= threshold) {
                        if (startY == -1) startY = y
                    } else {
                        if (startY != -1) {
                            val endY = y
                            if (endY - startY >= 4) {
                                addCropRegion(bitmap, crops, startY, endY, targetH, scale)
                            }
                            startY = -1
                        }
                    }
                }

                if (startY != -1 && targetH - startY >= 4) {
                    addCropRegion(bitmap, crops, startY, targetH, targetH, scale)
                }
            }
        } catch (e: Exception) {
            Log.w("SlipOcrEngine", "Detection model execution failed, falling back to full image: ${e.message}")
            return fallbackFullImageCrop(bitmap)
        } finally {
            scaledBitmap.recycle()
        }

        return if (crops.isNotEmpty()) crops else fallbackFullImageCrop(bitmap)
    }

    private fun addCropRegion(
        origBitmap: Bitmap,
        crops: MutableList<Pair<Rect, Bitmap>>,
        startY: Int,
        endY: Int,
        targetH: Int,
        scale: Float
    ) {
        val origTop = (startY / scale).toInt().coerceIn(0, origBitmap.height - 1)
        val origBottom = (endY / scale).toInt().coerceIn(origTop + 1, origBitmap.height)
        val cropH = origBottom - origTop
        val cropW = origBitmap.width

        if (cropH > 8 && cropW > 8) {
            val cropped = Bitmap.createBitmap(origBitmap, 0, origTop, cropW, cropH)
            crops.add(Pair(Rect(0, origTop, cropW, origBottom), cropped))
        }
    }

    private fun fallbackFullImageCrop(bitmap: Bitmap): List<Pair<Rect, Bitmap>> {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        return listOf(Pair(Rect(0, 0, bitmap.width, bitmap.height), copy))
    }

    private fun extractProbabilityMap(rawOutput: Any?, h: Int, w: Int): Array<FloatArray> {
        val map = Array(h) { FloatArray(w) }
        try {
            if (rawOutput is Array<*>) {
                val arr4d = rawOutput as? Array<Array<Array<FloatArray>>>
                if (arr4d != null && arr4d.isNotEmpty() && arr4d[0].isNotEmpty()) {
                    val floatMatrix = arr4d[0][0]
                    for (y in 0 until minOf(h, floatMatrix.size)) {
                        for (x in 0 until minOf(w, floatMatrix[y].size)) {
                            map[y][x] = floatMatrix[y][x]
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SlipOcrEngine", "Error parsing probMap tensor shape: ${e.message}")
        }
        return map
    }

    /**
     * Step 2: Text Recognition & CTC Greedy Decode for a single cropped bitmap.
     */
    private fun recognizeText(croppedBitmap: Bitmap): String {
        val session = recSession ?: return ""

        val recScale = 48.0f / croppedBitmap.height
        var recW = (croppedBitmap.width * recScale).toInt().coerceAtLeast(16)
        recW = (recW + 7) / 8 * 8

        val recBitmap = Bitmap.createScaledBitmap(croppedBitmap, recW, 48, true)

        try {
            val pixels = IntArray(recW * 48)
            recBitmap.getPixels(pixels, 0, recW, 0, 0, recW, 48)

            val floatBuffer = FloatBuffer.allocate(1 * 3 * 48 * recW)
            val mean = 0.5f
            val std = 0.5f

            for (c in 0 until 3) {
                val shift = (2 - c) * 8
                for (y in 0 until 48) {
                    for (x in 0 until recW) {
                        val p = pixels[y * recW + x]
                        val channelVal = (p shr shift and 0xFF) / 255.0f
                        floatBuffer.put((channelVal - mean) / std)
                    }
                }
            }
            floatBuffer.rewind()

            val inputName = session.inputNames.iterator().next() ?: "x"
            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 48, recW.toLong()))

            session.run(mapOf(inputName to inputTensor)).use { results ->
                val outputTensor = results.get(0).value
                return ctcDecode(outputTensor)
            }
        } catch (e: Exception) {
            Log.w("SlipOcrEngine", "Recognition model failed for crop: ${e.message}")
            return ""
        } finally {
            recBitmap.recycle()
        }
    }

    /**
     * CTC Greedy Decoding: Argmax over vocabulary per time-step, collapse consecutive duplicates, remove blank token (index 0).
     */
    private fun ctcDecode(rawOutput: Any?): String {
        val sb = StringBuilder()
        var lastIdx = -1

        try {
            if (rawOutput is Array<*>) {
                val arr3d = rawOutput as? Array<Array<FloatArray>>
                if (arr3d != null && arr3d.isNotEmpty()) {
                    val timesteps = arr3d[0]
                    for (t in timesteps.indices) {
                        val probs = timesteps[t]
                        var maxIdx = 0
                        var maxVal = Float.NEGATIVE_INFINITY
                        for (c in probs.indices) {
                            if (probs[c] > maxVal) {
                                maxVal = probs[c]
                                maxIdx = c
                            }
                        }

                        if (maxIdx != lastIdx) {
                            if (maxIdx > 0) {
                                val charIndex = maxIdx - 1
                                if (charIndex < dictionaryList.size) {
                                    sb.append(dictionaryList[charIndex])
                                }
                            }
                            lastIdx = maxIdx
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SlipOcrEngine", "CTC decode failed: ${e.message}")
        }
        return sb.toString()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            // 1. Read EXIF orientation first using a separate stream
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exifInterface = ExifInterface(stream)
                exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            } ?: ExifInterface.ORIENTATION_UNDEFINED

            // 2. Decode the bitmap
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // 3. Rotate the bitmap if needed
            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                rotatedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("SlipOcrEngine", "Failed to decode or rotate bitmap from URI: ${e.message}", e)
            null
        }
    }

    private fun loadAssetBytes(path: String): ByteArray? {
        return try {
            context.assets.open(path).use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAssetString(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
