package com.pennywiseai.tracker.slip.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * On-device Tesseract 5 (Tesseract4Android) OCR engine for Thai + English slips.
 *
 * Primary OCR engine of the hybrid pipeline: Tesseract reads Thai far more
 * accurately than PaddleOCR (see OCR_MEASUREMENT_REPORT.md — 7/8 amounts vs 5/8,
 * and it preserves Thai glyphs the parser needs for receiver/direction).
 *
 * Tesseract requires real file paths (not asset streams), so the tha/eng
 * traineddata are copied from assets/tessdata/ into the app's private files
 * directory once on first use.
 */
@Singleton
class TesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TesseractOcrEngine"
        private const val DATA_SUBDIR = "tesseract"
        private const val LANG = "tha+eng"
        private val TRAINEDDATA_FILES = listOf("tha.traineddata", "eng.traineddata")
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var tessApi: TessBaseAPI? = null

    @Volatile
    private var isReady = false

    /** Whether Tesseract initialised successfully and can be used. */
    val isAvailable: Boolean
        get() = isReady && tessApi != null

    init {
        ioScope.launch {
            initTesseract()
        }
    }

    private fun initTesseract() {
        try {
            val dataDir = File(context.filesDir, DATA_SUBDIR)
            val tessDataDir = File(dataDir, "tessdata")
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }

            // Copy traineddata from assets -> files dir (only once).
            TRAINEDDATA_FILES.forEach { name ->
                val target = File(tessDataDir, name)
                if (!target.exists() || target.length() == 0L) {
                    copyAssetToFile(name, target)
                }
            }

            val api = TessBaseAPI()
            // init returns false if the data path is wrong or langs are missing.
            val ok = api.init(dataDir.absolutePath, LANG)
            if (!ok) {
                Log.w(TAG, "TessBaseAPI.init failed (data path or language missing). Tesseract unavailable.")
                api.recycle()
                tessApi = null
                isReady = false
                return
            }
            // PSM 6 = single uniform block of text — matches a whole slip layout
            // and lets Tesseract use layout context across lines.
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            tessApi = api
            isReady = true
            Log.i(TAG, "Tesseract 5 initialized (tha+eng, PSM 6).")
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init failed: ${e.message}", e)
            isReady = false
        }
    }

    /**
     * Recognise the text in [bitmap] with Tesseract (tha+eng).
     * Returns "" when Tesseract is unavailable or recognition fails — callers
     * fall back to PaddleOCR in that case.
     */
    fun recognize(bitmap: Bitmap): String {
        val api = tessApi
        if (!isReady || api == null) return ""
        return try {
            api.setImage(bitmap)
            api.utF8Text ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Tesseract recognition failed: ${e.message}")
            ""
        }
    }

    private fun copyAssetToFile(assetName: String, target: File) {
        try {
            context.assets.open("tessdata/$assetName").use { input: InputStream ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied $assetName (${target.length()} bytes) to $target")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy asset $assetName: ${e.message}")
        }
    }

    /** Release native resources (idempotent). */
    fun shutdown() {
        try {
            tessApi?.recycle()
        } catch (_: Exception) {
        }
        tessApi = null
        isReady = false
    }
}
