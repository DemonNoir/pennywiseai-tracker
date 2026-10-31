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
import com.pennywiseai.tracker.domain.usecase.ProcessSlipUseCase
import com.pennywiseai.tracker.slip.SlipScanDataStore
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
    private val processSlipUseCase: ProcessSlipUseCase
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        val THAI_BANK_FOLDERS = listOf(
            "K PLUS",
            "SCB EASY",
            "Krungthai",
            "KMA",
            "Krungsri",
            "ttb touch",
            "GSB",
            "BAAC",
            "ธ.ก.ส.",
            "Bangkok Bank",
            "Bualuang",
            "BBL",
            "UOB",
            "TMRW",
            "CIMB",
            "LH Bank",
            "Dime",
            "Kept",
            "TrueMoney"
        )
    }

    private var isRegistered = false
    private var lastProcessedUri: Uri? = null

    // In-memory cache of processed image IDs, seeded from the persistent
    // SlipScanDataStore at registration so a process restart doesn't re-scan
    // slips that were already handled. Writes are mirrored to the store.
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

            // Seed the in-memory processed-set from the persistent store so slips
            // already scanned before an app-process death are not re-scanned.
            // Done synchronously (one-time, ~500-entry read at app start) so the
            // set is fully populated before any onChange callback can touch it —
            // both run on the main thread, avoiding a concurrent-modification race.
            try {
                runBlocking {
                    processedImageIds.addAll(SlipScanDataStore.getProcessedImageIds(context))
                }
                Log.d("SlipMediaObserver", "Seeded ${processedImageIds.size} previously processed image IDs.")
            } catch (e: Exception) {
                Log.w("SlipMediaObserver", "Failed to seed processed image IDs: ${e.message}")
            }
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

        val incomingId = uri?.let { extractImageId(it) }
        if (incomingId != null && processedImageIds.contains(incomingId)) {
            return
        }

        val targetUri = if (uri != null && incomingId != null) {
            if (!isBankFolderImage(uri)) {
                // Ignore images not in bank folders (e.g. Screenshots, Camera, Downloads)
                processedImageIds.add(incomingId)
                return
            }
            uri
        } else {
            getLatestImageUri() ?: return
        }

        if (targetUri == lastProcessedUri) return
        lastProcessedUri = targetUri

        val imageId = extractImageId(targetUri)
        if (imageId != null && processedImageIds.contains(imageId)) {
            return
        }

        ocrEngine.processImageUri(
            imageUri = targetUri,
            onSuccess = { parsedSlip ->
                if (imageId != null) {
                    processedImageIds.add(imageId)
                    observerScope.launch {
                        try {
                            SlipScanDataStore.addProcessedImageId(context, imageId)
                        } catch (e: Exception) {
                            Log.w("SlipMediaObserver", "Failed to persist processed image ID $imageId: ${e.message}")
                        }
                    }
                }
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

        // P0 Rule 1 & 3: Save to Room DB async using core logic and show notification
        observerScope.launch {
            try {
                val savedTransactionId = processSlipUseCase.execute(parsedSlip)
                
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
                Log.e("SlipMediaObserver", "Failed to save slip transaction: ${e.message}", e)
            }
        }
    }

    private fun isBankFolderImage(uri: Uri): Boolean {
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            pathColumn
        )

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val bucketName = if (bucketIndex >= 0) cursor.getString(bucketIndex) else null

                    val pathIndex = cursor.getColumnIndex(pathColumn)
                    val pathName = if (pathIndex >= 0) cursor.getString(pathIndex) else null

                    THAI_BANK_FOLDERS.any { folder ->
                        (bucketName != null && bucketName.contains(folder, ignoreCase = true)) ||
                        (pathName != null && pathName.contains(folder, ignoreCase = true))
                    }
                } else {
                    false
                }
            } ?: false
        } catch (e: Exception) {
            Log.w("SlipMediaObserver", "Error checking if uri is in bank folder: ${e.message}")
            false
        }
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
        
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }

        val selection = StringBuilder("(")
        val selectionArgs = mutableListOf<String>()
        THAI_BANK_FOLDERS.forEachIndexed { index, folder ->
            if (index > 0) selection.append(" OR ")
            selection.append("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?")
            selection.append(" OR $pathColumn LIKE ?")
            selectionArgs.add("%$folder%")
            selectionArgs.add("%$folder%")
        }
        selection.append(")")

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection.toString(),
            selectionArgs.toTypedArray(),
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
