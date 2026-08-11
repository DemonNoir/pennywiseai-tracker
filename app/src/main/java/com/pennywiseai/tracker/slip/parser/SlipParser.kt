package com.pennywiseai.tracker.slip.parser

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SlipDirection {
    INCOMING,     // รับเงิน / โอนเข้า
    OUTGOING,     // โอนเงินออก
    BILL_PAYMENT, // จ่ายบิล / ชำระสินค้า
    UNKNOWN
}

enum class SlipConfidence {
    CONFIRMED,    // ตรวจสอบถูกต้องครบถ้วน 100%
    HIGH,         // มีความน่าเชื่อถือสูง (มีธนาคาร ยอดเงิน และวันที่ครบ)
    NEEDS_REVIEW  // ควรตรวจสอบเพิ่มเติม (ข้อมูลบางส่วนไม่ครบ)
}

data class ParsedSlip(
    val bankName: String? = null,
    val transactionType: String? = null,
    val direction: SlipDirection = SlipDirection.UNKNOWN,
    val confidence: SlipConfidence = SlipConfidence.NEEDS_REVIEW,
    val date: String? = null,
    val time: String? = null,
    val dateTimeIso: String? = null,
    val timestampMillis: Long? = null,
    val refNo: String? = null,
    val ref1: String? = null,
    val ref2: String? = null,
    val ref3: String? = null,
    val senderName: String? = null,
    val senderAccount: String? = null,
    val receiverName: String? = null,
    val receiverAccount: String? = null,
    val billerId: String? = null,
    val merchantCode: String? = null,
    val branchCode: String? = null,
    val amount: Double? = null,
    val amountBigDecimal: BigDecimal? = null,
    val rawText: String = ""
)

data class SimpleParsedSlip(
    val receiverName: String? = null,
    val amount: Double? = null,
    val amountBigDecimal: BigDecimal? = null,
    val date: String? = null,
    val dateTimeIso: String? = null,
    val timestampMillis: Long? = null,
    val confidence: SlipConfidence = SlipConfidence.NEEDS_REVIEW
)

object SlipParser {

    private val thaiMonthMap = mapOf(
        "ม.ค." to 1, "มกราคม" to 1,
        "ก.พ." to 2, "กุมภาพันธ์" to 2,
        "มี.ค." to 3, "มีนาคม" to 3,
        "เม.ย." to 4, "เมษายน" to 4,
        "พ.ค." to 5, "พฤษภาคม" to 5,
        "มิ.ย." to 6, "มิถุนายน" to 6,
        "ก.ค." to 7, "กรกฎาคม" to 7,
        "ส.ค." to 8, "สิงหาคม" to 8,
        "ก.ย." to 9, "กันยายน" to 9,
        "ต.ค." to 10, "ตุลาคม" to 10,
        "พ.ย." to 11, "พฤศจิกายน" to 11,
        "ธ.ค." to 12, "ธันวาคม" to 12
    )

    private val textDatePattern = Regex("""^(\d{1,2})\s+([^\s\d]+)\s+(\d{2,4})$""")
    private val slashDatePattern = Regex("""^(\d{1,2})[\/\.-](\d{1,2})[\/\.-](\d{2,4})$""")

    private val dateTimeCombinedRegex = Regex(
        """(\d{1,2}\s+(?:ม\.ค\.|ก\.พ\.|มี\.ค\.|เม\.ย\.|พ\.ค\.|มิ\.ย\.|ก\.ค\.|ส\.ค\.|ก\.ย\.|ต\.ค\.|พ\.ย\.|ธ\.ค\.|มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม|[A-Za-z]{3,9})\s+\d{2,4}|\d{1,2}[\/\.-]\d{1,2}[\/\.-]\d{2,4})\s*[-,\s]+\s*(\d{1,2}:\d{2}(?::\d{2})?(?:\s*น\.)?)""",
        RegexOption.IGNORE_CASE
    )

    private val dateOnlyRegex = Regex(
        """(\d{1,2}\s+(?:ม\.ค\.|ก\.พ\.|มี\.ค\.|เม\.ย\.|พ\.ค\.|มิ\.ย\.|ก\.ค\.|ส\.ค\.|ก\.ย\.|ต\.ค\.|พ\.ย\.|ธ\.ค\.|มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม|[A-Za-z]{3,9})\s+\d{2,4})|(\d{1,2}[\/\.-]\d{1,2}[\/\.-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )

    private val timeOnlyRegex = Regex(
        """(?<=เวลา|Time|\b)(\d{1,2}:\d{2}(?::\d{2})?(?:\s*น\.)?)""",
        RegexOption.IGNORE_CASE
    )

    private val refNoRegex = Regex(
        """(?:รหัสอ้างอิง|เลขที่รายการ|Ref\.?\s*(?:No\.?)?|Transaction\s*ID|Trans\s*ID)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ref1Regex = Regex(
        """(?:เลขอ้างอิงที่\s*1|เลขที่อ้างอิง\s*1|Ref\.?\s*1)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ref2Regex = Regex(
        """(?:เลขอ้างอิงที่\s*2|เลขที่อ้างอิง\s*2|Ref\.?\s*2)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ref3Regex = Regex(
        """(?:เลขอ้างอิงที่\s*3|เลขที่อ้างอิง\s*3|Ref\.?\s*3)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val billerIdRegex = Regex(
        """(?:Biller\s*ID|เลขประจำตัวผู้เสียภาษี|บิลเลอร์\s*ไอดี)\s*[:\s]*([0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val merchantCodeRegex = Regex(
        """(?:รหัสร้านค้า|Merchant\s*ID|Merchant\s*Code)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val branchCodeRegex = Regex(
        """(?:รหัสสาขา|Branch\s*ID|Branch\s*Code)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val amountWithLabelRegex = Regex(
        """(?:จํานวนเงิน|จำนวนเงิน|จำนวน|ยอดเงิน|Amount)\s*[\n\r:]*\s*(?:THB|บาท)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val amountStandaloneRegex = Regex(
        """([\d,]+(?:\.\d{1,2})?)\s*(?:บาท|THB)""",
        RegexOption.IGNORE_CASE
    )

    private val accountNoRegex = Regex(
        """^[xX\d]{3,4}[-  ]*[xX\d]{1,4}[-  ]*[xX\d]{3,5}[-  ]*[xX\d]{0,4}$"""
    )

    private val inlineAccountNoRegex = Regex(
        """([xX\d]{3,4}[-  ]*[xX\d]{1,4}[-  ]*[xX\d]{3,5}[-  ]*[xX\d]{0,4})"""
    )

    private val knownAppNames = listOf(
        "SCB+", "SCB", "K+", "K PLUS", "KPLUS", "Krungthai NEXT", "ttb touch", "ttb", 
        "MyMo", "GSB", "BAY", "Krungsri", "Bangkok Bank", "Bualuang MBANK", "VISA", "MASTERCARD"
    )

    private val bankNamesList = listOf(
        "ธ.กสิกรไทย", "ธนาคารกสิกรไทย", "กสิกรไทย",
        "ธ.ไทยพาณิชย์", "ธนาคารไทยพาณิชย์", "ไทยพาณิชย์",
        "ธ.กรุงไทย", "ธนาคารกรุงไทย", "กรุงไทย",
        "ธ.กรุงเทพ", "ธนาคารกรุงเทพ", "กรุงเทพ",
        "ธ.กรุงศรี", "ธนาคารกรุงศรีอยุธยา", "กรุงศรีอยุธยา",
        "ธ.ออมสิน", "ธนาคารออมสิน", "ออมสิน",
        "ธ.ทหารไทยธนชาต", "ธนาคารทหารไทยธนชาต", "ttb",
        "ธ.เกียรตินาคิน", "ธนาคารเกียรตินาคินภัทร",
        "ธ.ก.ส.", "ธนาคารเพื่อการเกษตรและสหกรณ์การเกษตร",
        "ธ.อาคารสงเคราะห์"
    )

    fun parseSimple(text: String): SimpleParsedSlip {
        val full = parse(text)
        return SimpleParsedSlip(
            receiverName = full.receiverName,
            amount = full.amount,
            amountBigDecimal = full.amountBigDecimal,
            date = full.date,
            dateTimeIso = full.dateTimeIso,
            timestampMillis = full.timestampMillis,
            confidence = full.confidence
        )
    }

    fun parse(text: String): ParsedSlip {
        val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC)
        val lines = normalizedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val fullText = lines.joinToString("\n")

        val bankName = detectBankName(fullText)
        val transactionType = detectTransactionType(fullText)
        val direction = detectDirection(fullText)

        var date: String? = null
        var time: String? = null

        val dateTimeMatch = dateTimeCombinedRegex.find(fullText)
        if (dateTimeMatch != null) {
            date = dateTimeMatch.groupValues[1].trim()
            time = dateTimeMatch.groupValues[2].trim()
        } else {
            val dateMatch = dateOnlyRegex.find(fullText)
            if (dateMatch != null) {
                date = dateMatch.value.trim()
            }
            val timeMatch = timeOnlyRegex.find(fullText)
            if (timeMatch != null) {
                time = timeMatch.value.trim()
            }
        }

        val localDateTime = parseToLocalDateTime(date, time)
        val dateTimeIso = localDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val timestampMillis = localDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

        val refNo = refNoRegex.find(fullText)?.groupValues?.get(1)
        val ref1 = ref1Regex.find(fullText)?.groupValues?.get(1)
        val ref2 = ref2Regex.find(fullText)?.groupValues?.get(1)
        val ref3 = ref3Regex.find(fullText)?.groupValues?.get(1)

        val billerId = billerIdRegex.find(fullText)?.groupValues?.get(1)
        val merchantCode = merchantCodeRegex.find(fullText)?.groupValues?.get(1)
        val branchCode = branchCodeRegex.find(fullText)?.groupValues?.get(1)

        val (amount, amountBigDecimal) = extractAmount(fullText)
        val (senderName, senderAccount, receiverName, receiverAccount) = extractParties(lines)

        val confidence = evaluateConfidence(
            bankName = bankName,
            amount = amountBigDecimal,
            date = localDateTime,
            refNo = refNo,
            senderName = senderName,
            receiverName = receiverName,
            billerId = billerId,
            direction = direction
        )

        return ParsedSlip(
            bankName = bankName,
            transactionType = transactionType,
            direction = direction,
            confidence = confidence,
            date = date,
            time = time,
            dateTimeIso = dateTimeIso,
            timestampMillis = timestampMillis,
            refNo = refNo,
            ref1 = ref1,
            ref2 = ref2,
            ref3 = ref3,
            senderName = cleanNameString(senderName),
            senderAccount = senderAccount,
            receiverName = cleanNameString(receiverName),
            receiverAccount = receiverAccount,
            billerId = billerId,
            merchantCode = merchantCode,
            branchCode = branchCode,
            amount = amount,
            amountBigDecimal = amountBigDecimal,
            rawText = fullText
        )
    }

    private fun extractAmount(fullText: String): Pair<Double?, BigDecimal?> {
        val matches = mutableListOf<MatchResult>()
        amountWithLabelRegex.findAll(fullText).forEach { matches.add(it) }
        amountStandaloneRegex.findAll(fullText).forEach { matches.add(it) }

        // Fallback label regex matching common OCR variations (e.g., ยอดเงิน, ยอดโอน, ชำระ, Total)
        val fallbackLabelRegex = Regex(
            """(?:ยอดเงิน|ยอดโอน|ยอดรวม|ชำระ|จำนวน|จํานวน|ราคา|Total|Paid|Amount)\s*[\n\r:]*\s*(?:THB|บาท)?\s*([\d,]+\.\d{2})""",
            RegexOption.IGNORE_CASE
        )
        fallbackLabelRegex.findAll(fullText).forEach { matches.add(it) }

        if (matches.isNotEmpty()) {
            val bestMatch = matches.sortedBy { match ->
                val labelPos = fullText.indexOf("จำนวน", ignoreCase = true).takeIf { it != -1 }
                    ?: fullText.indexOf("ยอด", ignoreCase = true).takeIf { it != -1 }
                    ?: fullText.indexOf("บาท", ignoreCase = true).takeIf { it != -1 }
                    ?: 0
                Math.abs(match.range.first - labelPos)
            }.firstOrNull() ?: matches.first()

            val rawAmountStr = bestMatch.groupValues[1].replace(",", "")
            val bigDecimalValue = try {
                BigDecimal(rawAmountStr)
            } catch (e: Exception) {
                null
            }

            if (bigDecimalValue != null && bigDecimalValue > BigDecimal.ZERO) {
                return Pair(bigDecimalValue.toDouble(), bigDecimalValue)
            }
        }

        // Fallback: If no label match, find the largest decimal number formatted as XXX.XX (monetary amounts on slips are usually the largest)
        val decimalPattern = Regex("""\b(\d{1,3}(?:,\d{3})*|\d+)\.(\d{2})\b""")
        val candidates = decimalPattern.findAll(fullText).mapNotNull { match ->
            val str = match.groupValues[1].replace(",", "") + "." + match.groupValues[2]
            try {
                BigDecimal(str)
            } catch (e: Exception) {
                null
            }
        }.filter { it > BigDecimal.ZERO && it < BigDecimal("10000000") }.toList()

        if (candidates.isNotEmpty()) {
            val maxAmount = candidates.maxOrNull()
            if (maxAmount != null) {
                return Pair(maxAmount.toDouble(), maxAmount)
            }
        }

        return Pair(null, null)
    }

    fun parseToLocalDateTime(dateStr: String?, timeStr: String?): LocalDateTime? {
        if (dateStr == null) return null
        return try {
            val cleanDate = dateStr.trim()

            val textMatch = textDatePattern.find(cleanDate)
            val slashMatch = slashDatePattern.find(cleanDate)

            val (day, month, year) = when {
                textMatch != null -> {
                    val d = textMatch.groupValues[1].toInt()
                    val m = thaiMonthMap[textMatch.groupValues[2]] ?: 1
                    var rawYear = textMatch.groupValues[3].toInt()
                    if (rawYear < 100) rawYear += 2500
                    if (rawYear > 2400) rawYear -= 543
                    Triple(d, m, rawYear)
                }
                slashMatch != null -> {
                    val d = slashMatch.groupValues[1].toInt()
                    val m = slashMatch.groupValues[2].toInt()
                    var rawYear = slashMatch.groupValues[3].toInt()
                    if (rawYear < 100) rawYear += 2500
                    if (rawYear > 2400) rawYear -= 543
                    Triple(d, m, rawYear)
                }
                else -> return null
            }

            var hour = 0
            var minute = 0
            if (timeStr != null) {
                val cleanTime = timeStr.replace("น.", "").trim()
                val timeParts = cleanTime.split(":")
                if (timeParts.size >= 2) {
                    hour = timeParts[0].toIntOrNull() ?: 0
                    minute = timeParts[1].toIntOrNull() ?: 0
                }
            }

            val parsedDate = LocalDate.of(year, month, day)
            val parsedTime = LocalTime.of(hour, minute)
            LocalDateTime.of(parsedDate, parsedTime)
        } catch (e: Exception) {
            null
        }
    }

    private fun detectBankName(text: String): String? {
        return when {
            text.contains("SCB", ignoreCase = true) || text.contains("ไทยพาณิชย์") -> "SCB"
            text.contains("K+", ignoreCase = true) || text.contains("K PLUS", ignoreCase = true) || text.contains("กสิกรไทย") || text.contains("KBank", ignoreCase = true) -> "KBank"
            text.contains("Krungthai", ignoreCase = true) || text.contains("กรุงไทย") -> "Krungthai"
            text.contains("ttb", ignoreCase = true) || text.contains("ทหารไทยธนชาต") -> "ttb"
            text.contains("Bangkok Bank", ignoreCase = true) || text.contains("กรุงเทพ") -> "Bangkok Bank"
            text.contains("BAY", ignoreCase = true) || text.contains("กรุงศรี") -> "Krungsri"
            text.contains("KKP", ignoreCase = true) || text.contains("เกียรตินาคิน") -> "KKP"
            text.contains("GSB", ignoreCase = true) || text.contains("ออมสิน") -> "GSB"
            else -> null
        }
    }

    private fun detectTransactionType(text: String): String? {
        return when {
            text.contains("จ่ายบิลสำเร็จ") || text.contains("ชำระเงินสำเร็จ") || text.contains("ชำระสินค้า") || text.contains("บิลเลอร์") -> "จ่ายบิลสำเร็จ"
            text.contains("โอนเงินสำเร็จ") || text.contains("โอนสำเร็จ") -> "โอนเงินสำเร็จ"
            text.contains("รับเงินสำเร็จ") || text.contains("เงินเข้า") -> "รับเงินสำเร็จ"
            text.contains("เติมเงินสำเร็จ") -> "เติมเงินสำเร็จ"
            else -> "รายการสำเร็จ"
        }
    }

    private fun detectDirection(text: String): SlipDirection {
        return when {
            text.contains("จ่ายบิล") || text.contains("ชำระเงิน") || text.contains("ชำระสินค้า") || text.contains("Biller") || text.contains("Bill Payment", ignoreCase = true) -> SlipDirection.BILL_PAYMENT
            text.contains("รับเงินสำเร็จ") || text.contains("เงินเข้า") || text.contains("โอนเงินเข้า") || text.contains("รับโอน") || text.contains("Received", ignoreCase = true) -> SlipDirection.INCOMING
            text.contains("โอนเงินสำเร็จ") || text.contains("โอนสำเร็จ") || text.contains("โอนเงิน") || text.contains("Transfer Success", ignoreCase = true) -> SlipDirection.OUTGOING
            else -> SlipDirection.UNKNOWN
        }
    }

    private fun evaluateConfidence(
        bankName: String?,
        amount: BigDecimal?,
        date: LocalDateTime?,
        refNo: String?,
        senderName: String?,
        receiverName: String?,
        billerId: String?,
        direction: SlipDirection
    ): SlipConfidence {
        if (bankName == null || amount == null || date == null) {
            return SlipConfidence.NEEDS_REVIEW
        }

        val hasParties = when (direction) {
            SlipDirection.BILL_PAYMENT -> (billerId != null || receiverName != null)
            SlipDirection.INCOMING -> (senderName != null || receiverName != null)
            SlipDirection.OUTGOING -> (receiverName != null || senderName != null)
            SlipDirection.UNKNOWN -> (receiverName != null || senderName != null)
        }

        return when {
            refNo != null && hasParties -> SlipConfidence.CONFIRMED
            hasParties -> SlipConfidence.HIGH
            else -> SlipConfidence.NEEDS_REVIEW
        }
    }

    private fun extractParties(lines: List<String>): PartyInfo {
        var senderName: String? = null
        var senderAccount: String? = null
        var receiverName: String? = null
        var receiverAccount: String? = null

        val toIndex = lines.indexOfFirst { line ->
            line.contains("ไปยัง") || line.contains("โอนให้") || line.equals("To", ignoreCase = true)
        }

        val fromIndex = lines.indexOfFirst { line ->
            line.contains("จาก") || line.equals("From", ignoreCase = true)
        }

        if (toIndex != -1) {
            val senderStart = if (fromIndex != -1) fromIndex + 1 else 0
            val senderEnd = toIndex

            for (i in senderStart until senderEnd) {
                val line = lines[i]
                if (isSkipLine(line) || isBankNameLine(line)) continue

                val (namePart, accPart) = splitNameAndAccount(line)
                if (accPart != null && senderAccount == null) senderAccount = accPart
                if (namePart != null && senderName == null) senderName = namePart

                if (accPart == null && namePart == null) {
                    if (accountNoRegex.matches(line) || (line.contains("xxx", ignoreCase = true) && senderAccount == null)) {
                        senderAccount = line
                    } else if (senderName == null && isValidNameCandidate(line)) {
                        senderName = line
                    }
                }
            }

            val amountIndex = lines.indexOfFirst { line ->
                line.contains("จํานวนเงิน") || line.contains("จำนวนเงิน") || line.contains("Amount", ignoreCase = true)
            }

            val receiverEnd = if (amountIndex != -1 && amountIndex > toIndex) amountIndex else minOf(toIndex + 10, lines.size)

            for (i in (toIndex + 1) until receiverEnd) {
                val line = lines[i]
                if (isSkipLine(line) || isBankNameLine(line)) continue

                val (namePart, accPart) = splitNameAndAccount(line)
                if (accPart != null && receiverAccount == null) receiverAccount = accPart
                if (namePart != null && receiverName == null) receiverName = namePart

                if (accPart == null && namePart == null) {
                    if (accountNoRegex.matches(line) || (line.contains("xxx", ignoreCase = true) && receiverAccount == null)) {
                        receiverAccount = line
                    } else if (receiverName == null && isValidNameCandidate(line)) {
                        receiverName = line
                    }
                }
            }
        } else {
            for (line in lines) {
                if (isSkipLine(line) || isBankNameLine(line)) continue

                val (namePart, accPart) = splitNameAndAccount(line)
                if (namePart != null || accPart != null) {
                    if (senderName == null) {
                        senderName = namePart
                        senderAccount = accPart
                    } else if (receiverName == null && (namePart != senderName || accPart != senderAccount)) {
                        receiverName = namePart
                        receiverAccount = accPart
                    }
                } else if (isValidNameCandidate(line)) {
                    if (senderName == null) {
                        senderName = line
                    } else if (receiverName == null && line != senderName) {
                        receiverName = line
                    }
                } else if (accountNoRegex.matches(line) || line.contains("xxx", ignoreCase = true)) {
                    if (senderAccount == null) senderAccount = line
                    else if (receiverAccount == null) receiverAccount = line
                }
            }
        }

        return PartyInfo(senderName, senderAccount, receiverName, receiverAccount)
    }

    private fun splitNameAndAccount(line: String): Pair<String?, String?> {
        val match = inlineAccountNoRegex.find(line)
        if (match != null) {
            val acc = match.value.trim()
            val namePart = line.replace(acc, "").trim()
            val cleanName = if (namePart.length >= 3 && isValidNameCandidate(namePart)) namePart else null
            return Pair(cleanName, acc)
        }
        return Pair(null, null)
    }

    private fun cleanNameString(name: String?): String? {
        if (name == null) return null
        var cleaned = name
        val bankSuffixRegex = Regex("""\s*(?:ธนาคาร|ธ\.)\s*[\u0E00-\u0E7F]+\b""")
        cleaned = cleaned.replace(bankSuffixRegex, "").trim()
        return if (cleaned.isEmpty()) null else cleaned
    }

    private fun isBankNameLine(line: String): Boolean {
        val trimmed = line.trim()
        return bankNamesList.any { trimmed.equals(it, ignoreCase = true) || trimmed.startsWith("ธ.") || trimmed.startsWith("ธนาคาร") }
    }

    private fun isValidNameCandidate(line: String): Boolean {
        if (line.length < 3) return false
        if (knownAppNames.any { line.equals(it, ignoreCase = true) }) return false
        if (isBankNameLine(line)) return false

        if (line.contains("สำเร็จ") || 
            line.contains("รหัสอ้างอิง") || 
            line.contains("เลขที่รายการ") || 
            line.contains("จํานวนเงิน") || 
            line.contains("จำนวนเงิน") || 
            line.contains("จำนวน:") || 
            line.contains("ค่าธรรมเนียม") ||
            line.contains("ผู้รับเงินสามารถ")) return false

        val labelPrefixes = listOf(
            "Biller ID", "รหัสร้านค้า", "รหัสสาขา", "รหัสธุรกรรม", 
            "เลขที่อ้างอิง", "เลขอ้างอิง", "ค่าธรรมเนียม", "Ref", "Trans"
        )
        if (labelPrefixes.any { line.startsWith(it, ignoreCase = true) }) return false
        if (dateOnlyRegex.containsMatchIn(line) || timeOnlyRegex.containsMatchIn(line)) return false

        val namePrefixes = listOf(
            "นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", 
            "Mr.", "Mrs.", "Ms.", "Miss", "MR.", "MRS.", "MS.", 
            "บจก.", "บริษัท", "ร้าน", "TrueMoney", "AIS", "บัตรเครดิต", "Shop", "Store", "Co.,Ltd"
        )
        if (namePrefixes.any { line.startsWith(it, ignoreCase = true) }) return true

        val uppercaseCompanyPattern = Regex("""^[A-Z0-9\s\.\&\-]{3,}$""")
        if (uppercaseCompanyPattern.matches(line) && line.length >= 3) return true

        val namePattern = Regex("""^[\u0E00-\u0E7FA-Za-z0-9\s\(\)\.\/\&\–-]{3,}$""")
        return namePattern.matches(line)
    }

    private fun isSkipLine(line: String): Boolean {
        if (knownAppNames.any { line.equals(it, ignoreCase = true) }) return true

        val keywordsToSkip = listOf(
            "จ่ายบิลสำเร็จ", "ชำระเงินสำเร็จ", "โอนเงินสำเร็จ", "โอนสำเร็จ", "เติมเงินสำเร็จ", "รับเงินสำเร็จ",
            "จาก", "ไปยัง", "จํานวนเงิน", "จำนวนเงิน", "จำนวน:", "ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี้เพื่อตรวจสอบสถานะการจ่ายเงิน"
        )
        if (keywordsToSkip.any { line.equals(it, ignoreCase = true) }) return true

        val skipPrefixes = listOf(
            "รหัสอ้างอิง", "เลขที่รายการ", "เลขอ้างอิง", "เลขที่อ้างอิง", "รหัสธุรกรรม",
            "ค่าธรรมเนียม", "Ref", "Trans"
        )
        if (skipPrefixes.any { line.startsWith(it, ignoreCase = true) }) return true

        return false
    }

    private data class PartyInfo(
        val senderName: String?,
        val senderAccount: String?,
        val receiverName: String?,
        val receiverAccount: String?
    )
}
