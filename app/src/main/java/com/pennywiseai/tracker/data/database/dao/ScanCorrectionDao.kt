package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.ScanCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanCorrectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: ScanCorrectionEntity)

    @Query("SELECT * FROM scan_corrections WHERE bankName = :bankName AND fieldName = :fieldName ORDER BY createdAt DESC")
    fun getCorrections(bankName: String, fieldName: String): Flow<List<ScanCorrectionEntity>>

    @Query("""
        SELECT correctedValue, COUNT(*) as frequency 
        FROM scan_corrections 
        WHERE bankName = :bankName 
          AND fieldName = :fieldName 
          AND originalValue = :originalValue
        GROUP BY correctedValue 
        ORDER BY frequency DESC 
        LIMIT :limit
    """)
    fun getFrequentCorrections(bankName: String, fieldName: String, originalValue: String, limit: Int = 3): Flow<List<CorrectionSuggestion>>
}

data class CorrectionSuggestion(
    val correctedValue: String,
    val frequency: Int
)
