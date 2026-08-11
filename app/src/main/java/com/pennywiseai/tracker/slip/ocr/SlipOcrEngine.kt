package com.pennywiseai.tracker.slip.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.pennywiseai.tracker.slip.parser.ParsedSlip
import com.pennywiseai.tracker.slip.parser.SlipParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tesseract On-Device & Offline OCR Engine (Thai + English)
 * ถอดข้อความจากสลิปในเครื่อง 100% ไม่ส่งข้อมูลออกนอกเครื่อง
 */
@Singleton
class SlipOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private fun prepareTessData(): String {
        val tessDir = File(context.filesDir, "tessdata")
        if (!tessDir.exists()) {
            tessDir.mkdirs()
        }
        val assetsToCopy = listOf("tha.traineddata", "eng.traineddata")
        for (assetName in assetsToCopy) {
            val targetFile = File(tessDir, assetName)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                try {
                    context.assets.open("tessdata/$assetName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("SlipOcrEngine", "Copied asset $assetName to ${targetFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("SlipOcrEngine", "Failed to copy asset $assetName: ${e.message}", e)
                }
            }
        }
        return context.filesDir.absolutePath
    }

    fun processImageUri(
        imageUri: Uri,
        onSuccess: (ParsedSlip) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ioScope.launch {
            try {
                val dataPath = prepareTessData()
                val bitmap = loadBitmapFromUri(imageUri)
                    ?: throw IllegalArgumentException("Could not load bitmap from URI: $imageUri")

                val tess = TessBaseAPI()
                val initialized = tess.init(dataPath, "tha+eng")
                if (!initialized) {
                    bitmap.recycle()
                    throw IllegalStateException("Failed to initialize Tesseract with data at $dataPath")
                }

                tess.setImage(bitmap)
                val rawOcrText = tess.utF8Text ?: ""
                tess.recycle()
                bitmap.recycle()

                Log.d("SlipOcrEngine", "Tesseract extracted ${rawOcrText.length} characters from image $imageUri")
                Log.d("SlipOcrEngine", "Raw OCR Text: \n$rawOcrText")
                val parsedSlip = SlipParser.parse(rawOcrText)
                Log.d("SlipOcrEngine", "Parsed Result: Bank=${parsedSlip.bankName}, Amount=${parsedSlip.amount}, Confidence=${parsedSlip.confidence}")
                Log.d("SlipOcrEngine", "Parsed Names: Sender=${parsedSlip.senderName}, Receiver=${parsedSlip.receiverName}")

                withContext(Dispatchers.Main) {
                    onSuccess(parsedSlip)
                }
            } catch (e: Exception) {
                Log.e("SlipOcrEngine", "Tesseract OCR failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun processRawText(rawText: String): ParsedSlip {
        return SlipParser.parse(rawText)
    }

    suspend fun getRawTextSync(imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val dataPath = prepareTessData()
            val bitmap = loadBitmapFromUri(imageUri)
                ?: throw IllegalArgumentException("Could not load bitmap from URI: $imageUri")

            val tess = TessBaseAPI()
            val initialized = tess.init(dataPath, "tha+eng")
            if (!initialized) {
                bitmap.recycle()
                throw IllegalStateException("Failed to initialize Tesseract with data at $dataPath")
            }

            tess.setImage(bitmap)
            val rawOcrText = tess.utF8Text ?: ""
            tess.recycle()
            bitmap.recycle()
            
            rawOcrText
        } catch (e: Exception) {
            Log.e("SlipOcrEngine", "Sync OCR failed: ${e.message}", e)
            ""
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            Log.e("SlipOcrEngine", "Failed to decode bitmap from URI: ${e.message}", e)
            null
        }
    }
}

