package com.pennywiseai.tracker.slip.worker

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.domain.usecase.ProcessSlipUseCase
import com.pennywiseai.tracker.data.database.dao.SlipScanHistoryDao
import com.pennywiseai.tracker.data.database.entity.SlipScanHistoryEntity
import com.pennywiseai.tracker.slip.ocr.SlipOcrEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SlipAutoScanWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val ocrEngine: SlipOcrEngine,
    private val processSlipUseCase: ProcessSlipUseCase,
    private val slipScanHistoryDao: SlipScanHistoryDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SlipAutoScanWorker"
        const val WORK_NAME = "slip_auto_scan_work"
        
        // โฟลเดอร์มาตรฐานที่แอปธนาคารไทยใช้บันทึกสลิป
        private val THAI_BANK_FOLDERS = listOf(
            "K PLUS",
            "SCB EASY",
            "Krungthai",
            "KMA",
            "ttb touch",
            "GSB",
            "BAAC",
            "ธ.ก.ส."
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting Auto-Slip Scan...")
        
        try {
            // Wait for OCR engine to be ready before starting
            Log.d(TAG, "Waiting for OCR engine to initialize...")
            ocrEngine.waitForReady()
            Log.d(TAG, "OCR engine is ready.")

            val imageUris = queryRecentBankImages()
            Log.d(TAG, "Found ${imageUris.size} recent bank images to scan")

            var successCount = 0
            var failCount = 0
            
            for (uri in imageUris) {
                val uriString = uri.toString()
                val history = slipScanHistoryDao.getHistoryByUri(uriString)
                
                if (history != null) {
                    if (history.status == "SUCCESS" || history.attemptCount >= 3) {
                        Log.d(TAG, "Skipping previously scanned URI (status=${history.status}, attempts=${history.attemptCount}): $uri")
                        continue
                    }
                }

                val currentAttempts = history?.attemptCount ?: 0

                try {
                    val rawText = ocrEngine.getRawTextSync(uri)
                    val parsedSlip = ocrEngine.processRawText(rawText)
                    
                    if (parsedSlip.amount != null && (parsedSlip.amount ?: 0.0) > 0.0) {
                        processSlipUseCase.execute(parsedSlip)
                        
                        slipScanHistoryDao.insertOrUpdate(SlipScanHistoryEntity(
                            imageUri = uriString,
                            status = "SUCCESS",
                            attemptCount = currentAttempts + 1,
                            lastScanTime = System.currentTimeMillis()
                        ))
                        
                        successCount++
                    } else {
                        Log.d(TAG, "Rejected image at $uri: Not a valid slip or amount not found.")
                        Log.v(TAG, "Raw text from rejected image: \n$rawText")
                        
                        slipScanHistoryDao.insertOrUpdate(SlipScanHistoryEntity(
                            imageUri = uriString,
                            status = "FAILED_NO_AMOUNT",
                            attemptCount = currentAttempts + 1,
                            lastScanTime = System.currentTimeMillis()
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process image $uri: ${e.message}")
                    
                    slipScanHistoryDao.insertOrUpdate(SlipScanHistoryEntity(
                        imageUri = uriString,
                        status = "FAILED_ERROR",
                        attemptCount = currentAttempts + 1,
                        lastScanTime = System.currentTimeMillis()
                    ))
                    
                    failCount++
                }
            }

            Log.i(TAG, "Auto-Slip Scan Complete: Success=$successCount, Failed=$failCount")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in SlipAutoScanWorker: ${e.message}")
            Result.failure()
        }
    }

    private fun queryRecentBankImages(): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        
        // สแกนย้อนหลัง 7 วันเพื่อหาความแน่ใจ (Deduplication จะจัดการส่วนที่ซ้ำเอง)
        val sevenDaysAgo = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - TimeUnit.DAYS.toSeconds(7)
        
        // ค้นหาในโฟลเดอร์ที่ระบุ
        val selection = StringBuilder()
        selection.append("(${MediaStore.Images.Media.DATE_ADDED} >= ?) AND (")
        
        THAI_BANK_FOLDERS.forEachIndexed { index, _ ->
            if (index > 0) selection.append(" OR ")
            selection.append("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?")
            selection.append(" OR ${MediaStore.Images.Media.DATA} LIKE ?")
        }
        selection.append(")")

        val selectionArgs = mutableListOf<String>()
        selectionArgs.add(sevenDaysAgo.toString())
        THAI_BANK_FOLDERS.forEach { folder ->
            selectionArgs.add("%$folder%")
            selectionArgs.add("%$folder%")
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection.toString(),
                selectionArgs.toTypedArray(),
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && uris.size < 50) { // Limit to 50 recent images
                    val id = cursor.getLong(idColumn)
                    uris.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query bank folder images: ${e.message}", e)
        }
        return uris
    }
}
