package com.pennywiseai.tracker.slip

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistent record of MediaStore image IDs already processed by the slip scanner.
 *
 * Before this store existed, `SlipMediaObserver` kept its processed set only in
 * memory, so an app-process death re-scanned (and re-saved — deduped via the
 * transaction hash, but OCR'd again) every slip the user had already captured.
 *
 * The set is capped to the most recent [MAX_RECORDED_IDS] IDs; older entries are
 * dropped. Slip images are long-lived on disk, but re-scanning a slip that is old
 * enough to have aged out is harmless — `ProcessSlipUseCase` still dedupes by
 * transaction hash before inserting.
 */
object SlipScanDataStore {

    private const val MAX_RECORDED_IDS = 20000

    // DataStore preferences has no longSet key; image IDs are stored as strings.
    private val PROCESSED_IMAGE_IDS = stringSetPreferencesKey("processed_image_ids")

    private val Context.slipScanDataStore by preferencesDataStore(name = "slip_scan")

    /** All recorded processed image IDs (unordered set). */
    fun processedImageIds(context: Context): Flow<Set<Long>> {
        return context.slipScanDataStore.data.map { prefs ->
            (prefs[PROCESSED_IMAGE_IDS] ?: emptySet())
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        }
    }

    /** Snapshot read for one-shot observers (e.g. seeding the in-memory cache). */
    suspend fun getProcessedImageIds(context: Context): Set<Long> =
        processedImageIds(context).first()

    /** Record an image ID as processed, capping the set to the most recent N IDs. */
    suspend fun addProcessedImageId(context: Context, imageId: Long) {
        context.slipScanDataStore.edit { prefs ->
            val current = prefs[PROCESSED_IMAGE_IDS] ?: emptySet()
            val updated = (current + imageId.toString())
                // stringSetPreferencesKey stores an unordered set; when full, drop
                // arbitrary entries to make room for the new ID.
                .takeIf { it.size <= MAX_RECORDED_IDS }
                ?: (current.take(MAX_RECORDED_IDS - 1).toSet() + imageId.toString())
            prefs[PROCESSED_IMAGE_IDS] = updated
        }
    }

    private val SCAN_COUNT = intPreferencesKey("scan_count")

    /** Track how many times a catch-up scan has been started. */
    suspend fun incrementAndGetScanCount(context: Context): Int {
        var newCount = 0
        context.slipScanDataStore.edit { prefs ->
            val current = prefs[SCAN_COUNT] ?: 0
            newCount = current + 1
            prefs[SCAN_COUNT] = newCount
        }
        return newCount
    }
}
