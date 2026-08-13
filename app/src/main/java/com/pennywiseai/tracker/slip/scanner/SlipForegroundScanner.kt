package com.pennywiseai.tracker.slip.scanner

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.pennywiseai.tracker.domain.usecase.ProcessSlipUseCase
import com.pennywiseai.tracker.slip.SlipScanDataStore
import com.pennywiseai.tracker.slip.observer.SlipMediaObserver
import com.pennywiseai.tracker.slip.ocr.SlipOcrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SlipScanProgress(
    val total: Int,
    val current: Int,
    val isScanning: Boolean
)

@Singleton
class SlipForegroundScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrEngine: SlipOcrEngine,
    private val processSlipUseCase: ProcessSlipUseCase,
    private val mediaObserver: SlipMediaObserver
) {
    private val _progress = MutableStateFlow<SlipScanProgress?>(null)
    val progress: StateFlow<SlipScanProgress?> = _progress.asStateFlow()

    private var isScanning = false

    suspend fun startCatchUpScan() = withContext(Dispatchers.IO) {
        if (isScanning || !mediaObserver.hasStoragePermission()) return@withContext
        isScanning = true

        try {
            val processedIds = SlipScanDataStore.getProcessedImageIds(context)
            val urisToScan = queryUnscannedBankImages(processedIds)

            if (urisToScan.isNotEmpty()) {
                Log.i("SlipForegroundScanner", "Starting foreground scan for ${urisToScan.size} slips.")
                _progress.value = SlipScanProgress(total = urisToScan.size, current = 0, isScanning = true)
                var currentCount = 0

                for ((uri, mediaTimestamp) in urisToScan) {
                    try {
                        val rawOcrText = ocrEngine.getRawTextSync(uri)
                        var parsedSlip = com.pennywiseai.tracker.slip.parser.SlipParser.parse(rawOcrText).copy(imageUriString = uri.toString())
                        
                        if (parsedSlip.timestampMillis == null) {
                            parsedSlip = parsedSlip.copy(timestampMillis = mediaTimestamp)
                        }
                        
                        processSlipUseCase.execute(parsedSlip)
                    } catch (e: Exception) {
                        Log.e("SlipForegroundScanner", "Failed to process image $uri: ${e.message}")
                    } finally {
                        val imageId = uri.lastPathSegment?.toLongOrNull()
                        if (imageId != null) {
                            SlipScanDataStore.addProcessedImageId(context, imageId)
                        }
                        currentCount++
                        _progress.value = SlipScanProgress(total = urisToScan.size, current = currentCount, isScanning = true)
                    }
                }
            } else {
                Log.d("SlipForegroundScanner", "No new slips to scan.")
            }
        } catch (e: Exception) {
            Log.e("SlipForegroundScanner", "Scan failed: ${e.message}")
        } finally {
            _progress.value = null
            isScanning = false
        }
    }

    private fun queryUnscannedBankImages(processedIds: Set<Long>): List<Pair<Uri, Long>> {
        val results = mutableListOf<Pair<Uri, Long>>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val sevenDaysAgo = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - TimeUnit.DAYS.toSeconds(7)
        
        val selection = StringBuilder("(")
        val selectionArgs = mutableListOf<String>()

        SlipMediaObserver.THAI_BANK_FOLDERS.forEachIndexed { index, folder ->
            if (index > 0) selection.append(" OR ")
            selection.append("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?")
            selection.append(" OR ${MediaStore.Images.Media.DATA} LIKE ?")
            selectionArgs.add("%$folder%")
            selectionArgs.add("%$folder%")
        }
        selection.append(") AND ${MediaStore.Images.Media.DATE_ADDED} >= ?")
        selectionArgs.add(sevenDaysAgo.toString())

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
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) { // No limit to scan all available unscanned slips
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L
                    if (!processedIds.contains(id)) {
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        results.add(Pair(uri, dateAdded))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SlipForegroundScanner", "Failed to query unscanned images: ${e.message}", e)
        }
        return results
    }
}
