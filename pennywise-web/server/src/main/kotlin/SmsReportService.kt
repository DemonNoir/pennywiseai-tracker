package com.example

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.util.UUID
import org.postgresql.util.PGobject

class SmsReportService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_SMS_REPORTS = """
            CREATE TABLE IF NOT EXISTS sms_reports (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                sender_id VARCHAR(100) NOT NULL,
                message TEXT NOT NULL,
                parsed_result JSONB,
                user_expected JSONB NOT NULL,
                user_note TEXT,
                reviewed BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            )
        """

        private const val INSERT_REPORT = """
            INSERT INTO sms_reports (sender_id, message, parsed_result, user_expected, user_note)
            VALUES (?, ?, ?::jsonb, ?::jsonb, ?)
        """
    }

    init {
        // Create table if it doesn't exist
        try {
            val statement = connection.createStatement()
            statement.executeUpdate(CREATE_TABLE_SMS_REPORTS)
            statement.close()
        } catch (e: Exception) {
            // Table might already exist, which is fine
            println("Table initialization: ${e.message}")
        }
    }

    /**
     * Create a new SMS report
     */
    suspend fun createReport(request: CreateSmsReportRequest): String = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(INSERT_REPORT, Statement.RETURN_GENERATED_KEYS)

        try {
            // Set parameters
            statement.setString(1, request.senderId)
            statement.setString(2, request.message)

            // Convert parsed result to JSON (can be null)
            if (request.parsedResult != null) {
                val pgObject = PGobject()
                pgObject.type = "jsonb"
                pgObject.value = Json.encodeToString(request.parsedResult)
                statement.setObject(3, pgObject)
            } else {
                statement.setNull(3, Types.OTHER)
            }

            // Convert user expected to JSON (already a JsonObject)
            val pgObjectExpected = PGobject()
            pgObjectExpected.type = "jsonb"
            pgObjectExpected.value = Json.encodeToString(request.userExpected)
            statement.setObject(4, pgObjectExpected)

            // Set optional note
            if (request.userNote != null) {
                statement.setString(5, request.userNote)
            } else {
                statement.setNull(5, Types.VARCHAR)
            }

            statement.executeUpdate()

            val generatedKeys = statement.generatedKeys
            if (generatedKeys.next()) {
                return@withContext generatedKeys.getString(1)
            } else {
                return@withContext UUID.randomUUID().toString()
            }
        } finally {
            statement.close()
        }
    }
}