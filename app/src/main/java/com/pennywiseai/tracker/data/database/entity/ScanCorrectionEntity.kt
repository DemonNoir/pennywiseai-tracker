package com.pennywiseai.tracker.data.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "scan_corrections")
data class ScanCorrectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val fieldName: String,
    val originalValue: String,
    val correctedValue: String,
    val bankName: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
