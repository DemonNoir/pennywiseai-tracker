package com.pennywiseai.tracker.slip.observer

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.slip.notification.SlipNotificationManager
import com.pennywiseai.tracker.slip.ocr.SlipOcrEngine
import com.pennywiseai.tracker.slip.parser.ParsedSlip
import com.pennywiseai.tracker.slip.parser.SlipConfidence
import com.pennywiseai.tracker.slip.parser.SlipDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
    private val notificationManager: SlipNotificationManager,
    private val transactionRepository: TransactionRepository
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var isRegistered = false
    private var lastProcessedUri: Uri? = null
    private val processedImageIds = mutableSetOf<Long>()
    private val observerScope = CoroutineScope(Dispatchers.IO)

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun register() {
        if (isRegistered) return
        if (!hasStoragePermission()) {
            Log.w("SlipMediaObserver", "Storage permission not granted. Skipping MediaObserver registration.")
            return
        }

        try {
            val contentResolver: ContentResolver = context.contentResolver
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                this
            )
            isRegistered = true
            Log.i("SlipMediaObserver", "SlipMediaObserver registered successfully.")
        } catch (e: Exception) {
            Log.e("SlipMediaObserver", "Failed to register SlipMediaObserver: ${e.message}", e)
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.contentResolver.unregisterContentObserver(this)
            isRegistered = false
        } catch (e: Exception) {
            Log.e("SlipMediaObserver", "Failed to unregister SlipMediaObserver: ${e.message}", e)
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (!hasStoragePermission()) return

        val targetUri = uri ?: getLatestImageUri() ?: return
        if (targetUri == lastProcessedUri) return
        lastProcessedUri = targetUri

        val imageId = extractImageId(targetUri)
        if (imageId != null && processedImageIds.contains(imageId)) {
            return
        }

        ocrEngine.processImageUri(
            imageUri = targetUri,
            onSuccess = { parsedSlip ->
                if (imageId != null) processedImageIds.add(imageId)
                processParsedSlip(parsedSlip)
            },
            onError = { ex ->
                Log.d("SlipMediaObserver", "Image is not a slip or OCR failed: ${ex.message}")
                notificationManager.showSlipFailedNotification("เกิดข้อผิดพลาดในการถอดข้อมูลสลิป")
            }
        )
    }

    private fun processParsedSlip(parsedSlip: ParsedSlip) {
        // P0 Rule 3: Skip INCOMING (received money) & UNKNOWN directions
        if (parsedSlip.direction != SlipDirection.OUTGOING && parsedSlip.direction != SlipDirection.BILL_PAYMENT) {
            Log.d("SlipMediaObserver", "Skipping non-outgoing transaction (direction=${parsedSlip.direction})")
            return
        }

        val amt = parsedSlip.amountBigDecimal ?: parsedSlip.amount?.let { BigDecimal.valueOf(it) }
        if (amt == null || amt <= BigDecimal.ZERO) {
            Log.d("SlipMediaObserver", "Skipping slip: No valid amount extracted.")
            notificationManager.showSlipFailedNotification("พบสลิปใหม่แต่ไม่สามารถถอดจำนวนเงินได้")
            return
        }

        // P0 Rule 1 & 3: Save to Room DB async and show notification with real DB ID
        observerScope.launch {
            try {
                val savedTransactionId = saveToPennyWiseRoomDb(parsedSlip, amt)
                
                val statusPrefix = if (parsedSlip.confidence == SlipConfidence.CONFIRMED) {
                    "บันทึกสำเร็จ"
                } else {
                    "บันทึกแล้ว (รอตรวจสอบ)"
                }

                notificationManager.showSlipSavedNotification(
                    transactionId = savedTransactionId,
                    receiverName = parsedSlip.receiverName ?: parsedSlip.bankName ?: "สลิปโอนเงิน",
                    amountText = "$statusPrefix: ฿%.2f".format(amt.toDouble())
                )
            } catch (e: Exception) {
                Log.e("SlipMediaObserver", "Failed to save slip transaction to Room DB: ${e.message}", e)
            }
        }
    }

    private suspend fun saveToPennyWiseRoomDb(parsedSlip: ParsedSlip, amt: BigDecimal): Long {
        val dateTime = parsedSlip.timestampMillis?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        } ?: LocalDateTime.now()

        val merchant = parsedSlip.receiverName ?: parsedSlip.bankName ?: "Bank Slip Payment"
        val ref = parsedSlip.refNo ?: "SLIP_${System.currentTimeMillis()}"

        val entity = TransactionEntity(
            amount = amt,
            merchantName = merchant,
            category = "Uncategorized",
            transactionType = TransactionType.EXPENSE,
            dateTime = dateTime,
            description = "Slip Ref: $ref",
            bankName = parsedSlip.bankName ?: "Thai Bank",
            transactionHash = ref
        )

        return transactionRepository.insertTransaction(entity)
    }

    private fun extractImageId(uri: Uri): Long? {
        return try {
            uri.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun getLatestImageUri(): Uri? {
        if (!hasStoragePermission()) return null
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
}
