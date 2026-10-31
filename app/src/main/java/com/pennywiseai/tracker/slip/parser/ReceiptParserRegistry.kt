package com.pennywiseai.tracker.slip.parser

import java.math.BigDecimal
import java.time.LocalDateTime

enum class Confidence { HIGH, MEDIUM, LOW }

data class FieldResult<T>(
    val value: T,
    val confidence: Confidence
)

data class ParsedReceiptResult(
    val amount: FieldResult<BigDecimal>?,
    val payeeName: FieldResult<String>?,
    val refNo: FieldResult<String>?,
    val bank: FieldResult<String>?,
    val dateTime: FieldResult<LocalDateTime>?
)

interface BankReceiptTemplate {
    val bankId: String
    
    /**
     * Checks if this template can handle the given OCR raw text.
     */
    fun matches(rawText: String): Boolean
    
    /**
     * Parses the raw OCR text into strict fields with confidence levels.
     */
    fun parse(rawText: String): ParsedReceiptResult
}

object ReceiptParserRegistry {
    private val templates = mutableListOf<BankReceiptTemplate>()

    init {
        // Register known templates by default
        templates.add(KBankReceiptTemplate())
        templates.add(ScbReceiptTemplate())
    }

    fun register(template: BankReceiptTemplate) {
        templates.add(template)
    }

    /**
     * Finds the first matching bank template, or falls back to the generic legacy template.
     */
    fun findTemplate(rawText: String): BankReceiptTemplate {
        return templates.firstOrNull { it.matches(rawText) } ?: LegacyReceiptTemplate()
    }
}
