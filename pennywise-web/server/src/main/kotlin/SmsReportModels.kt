package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import java.time.Instant

/**
 * Data class representing a user-reported SMS that wasn't parsed correctly
 */
@Serializable
data class SmsReport(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val message: String,
    val parsedResult: JsonObject? = null,  // What our parser detected (null if not parsed)
    val userExpected: JsonObject,          // What user says it should parse to
    val userNote: String? = null,          // Optional note from user
    val reviewed: Boolean = false,
    val createdAt: String = Instant.now().toString()
)

/**
 * Request body for creating a new SMS report
 */
@Serializable
data class CreateSmsReportRequest(
    val senderId: String,
    val message: String,
    val parsedResult: JsonObject? = null,
    val userExpected: JsonObject,  // Changed to JsonObject to match frontend
    val userNote: String? = null
)

/**
 * What the user expects the SMS to parse to
 */
@Serializable
data class UserExpectedData(
    val amount: Double? = null,
    val type: String? = null,      // "INCOME" or "EXPENSE"
    val merchant: String? = null,
    val isTransaction: Boolean = true
)

/**
 * Response for SMS report creation
 */
@Serializable
data class SmsReportResponse(
    val success: Boolean,
    val message: String,
    val reportId: String? = null
)