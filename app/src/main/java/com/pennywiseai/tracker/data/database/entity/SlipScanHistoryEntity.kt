package com.pennywiseai.tracker.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "slip_scan_history")
data class SlipScanHistoryEntity(
    @PrimaryKey
    val imageUri: String,
    val status: String, // e.g., "SUCCESS", "FAILED_NO_AMOUNT", "FAILED_ERROR"
    val attemptCount: Int,
    val lastScanTime: Long
)
