package com.pennywiseai.tracker.slip.parser

import java.math.BigDecimal

class ScbReceiptTemplate : BankReceiptTemplate {
    override val bankId: String = "scb"

    override fun matches(rawText: String): Boolean {
        // SCB specific keywords
        return rawText.contains("SCB", ignoreCase = true) || 
               rawText.contains("ไทยพาณิชย์") ||
               rawText.contains("SCBEASY", ignoreCase = true)
    }

    override fun parse(rawText: String): ParsedReceiptResult {
        // Use generic parser to extract raw data first
        val parsedSlip = SlipParser.parse(rawText)
        
        // Apply strict confidence rules for SCB
        val amountConf = if (parsedSlip.amountBigDecimal != null) {
            if (rawText.contains("จำนวนเงิน") || rawText.contains("Amount")) Confidence.HIGH
            else Confidence.MEDIUM
        } else null

        val payeeConf = if (!parsedSlip.receiverName.isNullOrBlank()) {
            if (rawText.contains("ไปยัง") || rawText.contains("To")) Confidence.HIGH
            else Confidence.MEDIUM
        } else null

        // SCB ref numbers
        val refConf = if (!parsedSlip.refNo.isNullOrBlank()) {
            if (rawText.contains("รหัสอ้างอิง") || rawText.contains("Ref")) Confidence.HIGH
            else Confidence.MEDIUM
        } else null

        val dateTime = if (parsedSlip.date != null && parsedSlip.time != null) {
            SlipParser.parseToLocalDateTime(parsedSlip.date, parsedSlip.time)
        } else null

        val dateTimeConf = if (dateTime != null) Confidence.HIGH else null

        return ParsedReceiptResult(
            amount = parsedSlip.amountBigDecimal?.let { FieldResult(it, amountConf ?: Confidence.LOW) },
            payeeName = parsedSlip.receiverName?.let { FieldResult(it, payeeConf ?: Confidence.LOW) },
            refNo = parsedSlip.refNo?.let { FieldResult(it, refConf ?: Confidence.LOW) },
            bank = FieldResult("SCB", Confidence.HIGH), // We know it's SCB from matches()
            dateTime = dateTime?.let { FieldResult(it, dateTimeConf ?: Confidence.LOW) }
        )
    }
}
