package com.pennywiseai.tracker.slip.parser

import java.math.BigDecimal

class KBankReceiptTemplate : BankReceiptTemplate {
    override val bankId: String = "kbank"

    override fun matches(rawText: String): Boolean {
        // KBank specific keywords
        return rawText.contains("K PLUS", ignoreCase = true) || 
               rawText.contains("ธ.กสิกรไทย") ||
               rawText.contains("กสิกรไทย")
    }

    override fun parse(rawText: String): ParsedReceiptResult {
        // Use generic parser to extract raw data first
        val parsedSlip = SlipParser.parse(rawText)
        
        // Apply strict confidence rules for KBank
        val amountConf = if (parsedSlip.amountBigDecimal != null) {
            if (rawText.contains("จำนวนเงิน") || rawText.contains("จำนวน:")) Confidence.HIGH
            else Confidence.MEDIUM
        } else null

        val payeeConf = if (!parsedSlip.receiverName.isNullOrBlank()) {
            if (rawText.contains("ไปยัง") || rawText.contains("ผู้รับเงิน")) Confidence.HIGH
            else Confidence.MEDIUM
        } else null

        // KBank ref numbers are usually 18-20 characters long and alphanumeric
        val refConf = if (!parsedSlip.refNo.isNullOrBlank()) {
            if (parsedSlip.refNo.length >= 15 && rawText.contains("เลขที่รายการ")) Confidence.HIGH
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
            bank = FieldResult("KBank", Confidence.HIGH), // We know it's KBank from matches()
            dateTime = dateTime?.let { FieldResult(it, dateTimeConf ?: Confidence.LOW) }
        )
    }
}
