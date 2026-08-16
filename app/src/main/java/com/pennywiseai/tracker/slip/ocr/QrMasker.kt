package com.pennywiseai.tracker.slip.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Utility to find and mask QR codes in an image before running OCR.
 * This prevents OCR engines (like Tesseract) from hallucinating garbage text from QR code patterns.
 */
object QrMasker {
    private const val TAG = "QrMasker"

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    /**
     * Detects QR codes in the image and draws a white solid rectangle over them.
     * Returns a new Bitmap with the QR codes removed (if any were found).
     *
     * IMPORTANT: This method uses Tasks.await() and MUST be called on a background thread.
     */
    fun maskQrCodes(source: Bitmap, paddingPx: Int = 12): Bitmap {
        val inputImage = InputImage.fromBitmap(source, 0)

        val barcodes: List<Barcode> = try {
            Tasks.await(scanner.process(inputImage)) // sync call, must run in background thread
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan for QR codes: ${e.message}")
            emptyList()
        }

        if (barcodes.isEmpty()) return source

        // Work on a copy so we don't modify the potentially immutable source bitmap
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val whitePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        for (barcode in barcodes) {
            barcode.boundingBox?.let { box ->
                val padded = Rect(
                    (box.left - paddingPx).coerceAtLeast(0),
                    (box.top - paddingPx).coerceAtLeast(0),
                    (box.right + paddingPx).coerceAtMost(result.width),
                    (box.bottom + paddingPx).coerceAtMost(result.height)
                )
                canvas.drawRect(padded, whitePaint)
                Log.d(TAG, "Masked QR code at $padded")
            }
        }

        return result
    }
}
