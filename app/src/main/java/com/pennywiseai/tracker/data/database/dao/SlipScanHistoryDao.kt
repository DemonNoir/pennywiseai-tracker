package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.SlipScanHistoryEntity

@Dao
interface SlipScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: SlipScanHistoryEntity)

    @Query("SELECT * FROM slip_scan_history WHERE imageUri = :uri")
    suspend fun getHistoryByUri(uri: String): SlipScanHistoryEntity?
}
