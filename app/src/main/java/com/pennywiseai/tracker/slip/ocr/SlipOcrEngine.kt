package com.pennywiseai.tracker.slip.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pennywiseai.tracker.slip.parser.ParsedSlip
import com.pennywiseai.tracker.slip.parser.SlipParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit On-Device & Offline OCR Engine
 * ถอดข้อความจากสลิปในเครื่อง 100% ไม่ส่งข้อมูลออกนอกเครื่อง
 */
@Singleton
class SlipOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun processImageUri(
        imageUri: Uri,
        onSuccess: (ParsedSlip) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawOcrText = visionText.text
                    val parsedSlip = SlipParser.parse(rawOcrText)
                    onSuccess(parsedSlip)
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }
}
