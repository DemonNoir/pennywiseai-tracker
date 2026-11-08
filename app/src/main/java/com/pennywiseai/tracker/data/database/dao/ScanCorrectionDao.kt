package com.pennywiseai.tracker.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.pennywiseai.tracker.data.database.entity.ScanCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanCorrectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: ScanCorrectionEntity): Long

    @Query("SELECT * FROM scan_corrections WHERE fieldName = 'merchantName' ORDER BY createdAt DESC, id DESC")
    fun observeMerchantCorrections(): Flow<List<ScanCorrectionEntity>>

    @Query("SELECT * FROM scan_corrections WHERE fieldName = 'merchantName' ORDER BY id DESC")
    suspend fun getMerchantCorrectionsList(): List<ScanCorrectionEntity>

    @Query("SELECT * FROM scan_corrections WHERE id = :id LIMIT 1")
    suspend fun getCorrectionById(id: Long): ScanCorrectionEntity?

    @Query("SELECT * FROM scan_corrections WHERE fieldName = :fieldName")
    suspend fun getCorrectionsByField(fieldName: String): List<ScanCorrectionEntity>

    @Query("SELECT * FROM scan_corrections WHERE fieldName LIKE 'merchantBackfill:%'")
    suspend fun getMerchantProvenance(): List<ScanCorrectionEntity>

    @Query("DELETE FROM scan_corrections WHERE id = :id")
    suspend fun deleteCorrection(id: Long)

    @Query("DELETE FROM scan_corrections WHERE fieldName = :fieldName")
    suspend fun deleteCorrectionsByField(fieldName: String)

    @Query("DELETE FROM scan_corrections WHERE transactionId = :transactionId AND fieldName LIKE 'merchantBackfill:%'")
    suspend fun deleteMerchantProvenanceForTransaction(transactionId: Long)

    @Query("DELETE FROM scan_corrections")
    suspend fun clearAllCorrections()

    @Query("SELECT * FROM scan_corrections WHERE bankName = :bankName AND fieldName = :fieldName ORDER BY createdAt DESC")
    fun getCorrections(bankName: String, fieldName: String): Flow<List<ScanCorrectionEntity>>

    @Query("SELECT * FROM scan_corrections WHERE bankName = :bankName AND fieldName = :fieldName ORDER BY createdAt DESC")
    suspend fun getCorrectionsList(bankName: String, fieldName: String): List<ScanCorrectionEntity>

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
