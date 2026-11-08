package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.ScanCorrectionEntity
import com.pennywiseai.tracker.data.database.entity.SlipScanHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * An old install's rows survive every hop to [SCHEMA_VERSION] on the device's
 * own SQLite (run it on the Api26_Migration AVD: SQLite 3.18), opened the way
 * the app opens it.
 */
@RunWith(AndroidJUnit4::class)
class FullUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val helper = pennyWiseMigrationHelper(instrumentation, NAME)

    @After fun cleanUp() { instrumentation.targetContext.deleteDatabase(NAME) }

    @Test fun upgradeFrom47KeepsRows() = runBlocking {
        helper.createDatabase(47).apply {
            execSQL("""INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                transaction_hash, is_recurring, created_at, updated_at, bank_name, account_number) VALUES
                (1, '250.75', 'Synthetic store', 'Food', 'EXPENSE', '2025-01-01T10:00:00', 'synthetic-1', 0,
                '2025-01-01T10:00:00', '2025-01-01T10:00:00', 'Example Bank', '0000')""")
            execSQL("""INSERT INTO account_balances (id, bank_name, account_last4, balance, timestamp, created_at)
                VALUES (1, 'Example Bank', '0000', '1000.50', '2025-01-01T10:00:00', '2025-01-01T10:00:00')""")
            close()
        }

        val db = openDatabase()
        try {
            val txn = db.transactionDao().getTransactionById(1)!!
            assertEquals(0, BigDecimal("250.75").compareTo(txn.amount))
            assertEquals("Synthetic store", txn.merchantName)
            val balance = db.accountBalanceDao().getLatestBalance("Example Bank", "0000")!!
            assertEquals(0, BigDecimal("1000.50").compareTo(balance.balance))
            val version = db.useReaderConnection { c -> c.usePrepared("PRAGMA user_version") { it.step(); it.getLong(0) } }
            assertEquals(SCHEMA_VERSION.toLong(), version)

            val history = SlipScanHistoryEntity("content://test/slip", "SUCCESS", 1, 1000L)
            db.slipScanHistoryDao().insertOrUpdate(history)
            assertEquals(history, db.slipScanHistoryDao().getHistoryByUri(history.imageUri))

            val correction = ScanCorrectionEntity(
                transactionId = 1L,
                fieldName = "merchantName",
                originalValue = "Synthetic st0re",
                correctedValue = "Synthetic store",
                bankName = "Example Bank",
                createdAt = LocalDateTime.of(2025, 1, 1, 10, 0)
            )
            db.scanCorrectionDao().insertCorrection(correction)
            val saved = db.scanCorrectionDao().getCorrectionsList(correction.bankName, correction.fieldName).single()
            assertEquals(correction, saved.copy(id = 0))
        } finally { db.close() }
    }

    @Test fun customVersion59KeepsSlipHistory() = runBlocking {
        createLegacyCustomDatabase(version = 59, identity = "5ff4536c39d8852333ed3eeec9452675")

        val db = openDatabase()
        try {
            assertSlipHistory(db)
        } finally { db.close() }
    }

    @Test fun customVersion60KeepsSlipHistoryAndCorrections() = runBlocking {
        createLegacyCustomDatabase(version = 60, identity = "ea97bb3fd47dcb54fdf1b811e2335839")

        val db = openDatabase()
        try {
            assertSlipHistory(db)
            assertScanCorrection(db)
        } finally { db.close() }
    }

    @Test fun room2Version68KeepsSlipHistoryAndCorrections() = runBlocking {
        helper.createDatabase(68).use {
            // Use the identity recorded by Room 2 even if a later compiler changes its export.
            it.execSQL("UPDATE room_master_table SET identity_hash = 'ad7ffbc129459bc68013cea22eb6b8ae' WHERE id = 42")
            it.insertSlipHistory()
            it.insertScanCorrection()
        }

        val db = openDatabase()
        try {
            assertSlipHistory(db)
            assertScanCorrection(db)
        } finally { db.close() }
    }

    private suspend fun createLegacyCustomDatabase(version: Int, identity: String) {
        // Both branches shared v58. The custom v59/v60 added these slip tables
        // instead of upstream's recurring_transactions and categories.is_hidden.
        helper.createDatabase(58).use {
            it.execSQL("""
                CREATE TABLE slip_scan_history (
                    imageUri TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL, lastScanTime INTEGER NOT NULL
                )
            """.trimIndent())
            it.insertSlipHistory()
            if (version == 60) {
                it.execSQL("""
                    CREATE TABLE scan_corrections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL, fieldName TEXT NOT NULL, originalValue TEXT NOT NULL,
                        correctedValue TEXT NOT NULL, bankName TEXT NOT NULL, createdAt TEXT NOT NULL
                    )
                """.trimIndent())
                it.insertScanCorrection()
            }
            it.execSQL("UPDATE room_master_table SET identity_hash = '$identity' WHERE id = 42")
            it.execSQL("PRAGMA user_version = $version")
        }
    }

    private fun SQLiteConnection.insertSlipHistory() {
        execSQL("""
            INSERT INTO slip_scan_history (imageUri, status, attemptCount, lastScanTime)
            VALUES ('content://test/slip', 'SUCCESS', 2, 1000)
        """.trimIndent())
    }

    private fun SQLiteConnection.insertScanCorrection() {
        execSQL("""
            INSERT INTO scan_corrections
                (id, transactionId, fieldName, originalValue, correctedValue, bankName, createdAt)
            VALUES (1, 1, 'merchantName', 'Synthetic st0re', 'Synthetic store', 'Example Bank', '2025-01-01T10:00:00')
        """.trimIndent())
    }

    private suspend fun assertSlipHistory(db: PennyWiseDatabase) {
        val history = db.slipScanHistoryDao().getHistoryByUri("content://test/slip")
        assertEquals(SlipScanHistoryEntity("content://test/slip", "SUCCESS", 2, 1000L), history)
    }

    private suspend fun assertScanCorrection(db: PennyWiseDatabase) {
        val correction = db.scanCorrectionDao().getCorrectionsList("Example Bank", "merchantName").single()
        assertEquals("Synthetic store", correction.correctedValue)
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0), correction.createdAt)
    }

    private fun openDatabase(): PennyWiseDatabase =
        Room.databaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java, NAME)
            .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
            .setDriver(AndroidSQLiteDriver())
            .build()

    private companion object { const val NAME = "full-upgrade-test" }
}
