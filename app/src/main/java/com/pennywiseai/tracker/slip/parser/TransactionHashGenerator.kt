package com.pennywiseai.tracker.slip.parser

import java.security.MessageDigest

object TransactionHashGenerator {
    
    /**
     * Generates a stable hash for a parsed slip to prevent duplicate transactions.
     * Prioritizes `refNo` if available and valid. Otherwise, creates a composite hash.
     */
    fun generate(parsedSlip: ParsedSlip): String {
        val refNo = parsedSlip.refNo
        if (!refNo.isNullOrBlank() && refNo.length >= 5) {
            return refNo
        }

        // Fallback to Composite Hash
        val amountStr = parsedSlip.amountBigDecimal?.toPlainString() ?: "0.00"
        val dateStr = parsedSlip.date?.trim() ?: "nodate"
        val timeStr = parsedSlip.time?.trim() ?: "notime"
        val bankStr = parsedSlip.bankName?.trim()?.replace(" ", "") ?: "nobank"

        val compositeInput = "COMPOSITE_${amountStr}_${dateStr}_${timeStr}_$bankStr"
        return "COMPOSITE_" + md5(compositeInput)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") {
            String.format("%02x", it)
        }
    }
}
