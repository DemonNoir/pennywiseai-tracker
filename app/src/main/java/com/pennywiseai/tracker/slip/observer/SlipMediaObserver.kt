package com.pennywiseai.tracker.slip.observer

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.pennywiseai.tracker.slip.notification.SlipNotificationManager
import com.pennywiseai.tracker.slip.ocr.SlipOcrEngine
import com.pennywiseai.tracker.slip.parser.SlipConfidence
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background MediaStore ContentObserver
 * ตรวจจับรูปสลิปใหม่ที่ถูกบันทึกลงเครื่องทันที รันแบบอัตโนมัติ
 */
@Singleton
class SlipMediaObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrEngine: SlipOcrEngine,
    private val notificationManager: SlipNotificationManager
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var isRegistered = false
    private var lastProcessedUri: Uri? = null

    fun register() {
        if (isRegistered) return
        val contentResolver: ContentResolver = context.contentResolver
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        context.contentResolver.unregisterContentObserver(this)
        isRegistered = false
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val targetUri = uri ?: getLatestImageUri() ?: return

        if (targetUri == lastProcessedUri) return
        lastProcessedUri = targetUri

        ocrEngine.processImageUri(
            imageUri = targetUri,
            onSuccess = { parsedSlip ->
                if (parsedSlip.amountBigDecimal != null && parsedSlip.confidence != SlipConfidence.NEEDS_REVIEW) {
                    val savedTransactionId = saveToPennyWiseRoomDb(parsedSlip)

                    notificationManager.showSlipSavedNotification(
                        transactionId = savedTransactionId,
                        receiverName = parsedSlip.receiverName ?: "ร้านค้า/ผู้รับเงิน",
                        amountText = "${parsedSlip.amountBigDecimal} บาท"
                    )
                }
            },
            onError = {
                // Not a slip image
            }
        )
    }

    private fun getLatestImageUri(): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    private fun saveToPennyWiseRoomDb(parsedSlip: com.pennywiseai.tracker.slip.parser.ParsedSlip): Long {
        return System.currentTimeMillis()
    }
}
