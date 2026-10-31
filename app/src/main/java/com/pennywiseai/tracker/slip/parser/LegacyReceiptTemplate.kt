package com.pennywiseai.tracker.slip.parser

/**
 * Fallback template that wraps the existing generic `SlipParser`.
 * This ensures 100% backward compatibility when no specific bank template matches.
 */
class LegacyReceiptTemplate : BankReceiptTemplate {
    override val bankId: String = "generic"

    override fun matches(rawText: String): Boolean {
        // Fallback template always matches if evaluated last
        return true
    }

    override fun parse(rawText: String): ParsedReceiptResult {
        // Call the legacy parser
        val parsedSlip = SlipParser.parse(rawText)

        // Parse dateTime using the legacy logic
        val dateTime = if (parsedSlip.date != null && parsedSlip.time != null) {
            SlipParser.parseToLocalDateTime(parsedSlip.date, parsedSlip.time)
        } else {
            null
        }

        // We assign HIGH confidence to mimic the legacy behavior where it 
        // always overwrites fields without asking if it found a value.
        return ParsedReceiptResult(
            amount = parsedSlip.amountBigDecimal?.let { FieldResult(it, Confidence.HIGH) },
            payeeName = parsedSlip.receiverName?.let { FieldResult(it, Confidence.HIGH) },
            refNo = parsedSlip.refNo?.let { FieldResult(it, Confidence.HIGH) },
            bank = parsedSlip.bankName?.let { FieldResult(it, Confidence.HIGH) },
            dateTime = dateTime?.let { FieldResult(it, Confidence.HIGH) }
        )
    }
}
