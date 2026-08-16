package com.pennywiseai.tracker.slip.parser

import android.util.Log
import java.math.BigDecimal
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.ThaiBuddhistChronology
import java.time.chrono.ThaiBuddhistDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField

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
    /** ค่าสะดวกสำหรับ UI เก่า — แหล่งความจริงคือ [amountBigDecimal] */
    val amount: Double? = null,
    val amountBigDecimal: BigDecimal? = null,
    val rawText: String = "",
    val imageUriString: String? = null
)

data class SimpleParsedSlip(
    val receiverName: String? = null,
    val amount: Double? = null,
    val amountBigDecimal: BigDecimal? = null,
    val date: String? = null,
    val dateTimeIso: String? = null,
    val timestampMillis: Long? = null,
    val confidence: SlipConfidence = SlipConfidence.NEEDS_REVIEW,
    val imageUriString: String? = null
)

object SlipParser {

    private const val TAG = "SlipParser"

    private val leadingJunkRegex = Regex("""^[\<\>\-\=\:\s]+""")

    // คำนำหน้าคน/นิติบุคคล — ใช้ทั้งจัดอันดับชื่อและ isValidNameCandidate
    private val personOrgPrefixes = listOf(
        "นาย", "นางสาว", "นาง", "ด.ช.", "ด.ญ.",
        "Mr.", "Mrs.", "Ms.", "Miss", "MR.", "MRS.", "MS.",
        "Mr", "Mrs", "Ms", "บจก.", "บจก", "บริษัท"
    )

    private val namePrefixes = personOrgPrefixes + listOf(
        "ร้าน", "TrueMoney", "AIS", "บัตรเครดิต", "Shop", "Store", "Co.,Ltd"
    )

    private val junkNameKeywords = listOf(
        "พรบ", "ลง", "สลิป", "รายการ", "ตรวจสอบ", "สำเร็จ",
        "จํานวนเงิน", "จำนวนเงิน", "ค่าธรรมเนียม", "ผู้รับเงินสามารถ"
    )

    private val labelPrefixes = listOf(
        "Biller ID", "รหัสร้านค้า", "รหัสสาขา", "รหัสธุรกรรม",
        "เลขที่อ้างอิง", "เลขอ้างอิง", "ค่าธรรมเนียม", "Ref", "Trans", "พรบ"
    )

    // ตัดเฉพาะช่วงค่าธรรมเนียม + ตัวเลขของมัน ไม่กินข้ามบรรทัดถ้าไม่จำเป็น เพื่อป้องกันไปลบยอดจริงที่อยู่บรรทัดถัดไป
    private val feeSnippetRegex = Regex(
        """(?i)(?:ค่า)?[^\n]{0,12}(?:รรมเน|fee)[ \t]*[: \t]*[\d,]*(?:\.\d{2})?[ \t]*(?:บาท|THB)?"""
    )

    private val fallbackLabelRegex = Regex(
        """(?:ยอดเ?งน|ยอดโอน|ยอดรวม|ชาระ|จา?[นบ]วน|ราคา|Total|Paid|Amount)\s*[\n\r:\-]*\s*(?:THB|บาท)?\s*([\d,]+\.\d{2})""",
        RegexOption.IGNORE_CASE
    )

    private val decimalAmountRegex = Regex("""\b(\d{1,3}(?:,\d{3})*|\d+)\.(\d{2})(?!\d)""")

    private val amountNoiseLineRegex = Regex(
        """(?i)(?:รห[สศล]อางอง|เลขทรายการ|รหัสอ้างอิง|เลขที่รายการ|Biller|B1er|xxx|Ref|Trans|โทร|เบอร์)"""
    )

    // เวลาใช้ formatter ของ java.time เท่านั้น — ไม่แตกชั่วโมง/นาทีเอง
    private val slipTimeHmss = DateTimeFormatter.ofPattern("H:mm:ss")
    private val slipTimeHm = DateTimeFormatter.ofPattern("H:mm")
    private val slipTimeHHmm = DateTimeFormatter.ofPattern("HHmm")

    // ปี พ.ศ. 2 หลัก (เช่น 69) ขยายด้วย reduced year ของ ThaiBuddhistChronology ไม่บวก 2500/ลบ 543 เอง
    private val beTwoDigitYearFormatter = DateTimeFormatterBuilder()
        .appendValueReduced(ChronoField.YEAR_OF_ERA, 2, 2, ThaiBuddhistDate.now())
        .toFormatter()
        .withChronology(ThaiBuddhistChronology.INSTANCE)
        .withResolverStyle(ResolverStyle.STRICT)

    // สระ/วรรณยุกต์ที่อยู่บน-ล่างพยัญชนะ — OCR มักตกหล่น จึงตัดออกตอนจับ label
    // ไม่ตัด เ แ โ ใ ไ เพราะเป็นสระหน้าและใช้แยกคำ
    private val thaiMarksRegex = Regex("""[\u0E31\u0E33-\u0E3A\u0E47-\u0E4E]""")

    // เดือนแบบย่อ: จุดและช่องว่างเป็น optional เพราะ OCR ชอบได้ "ส ค" / "ส.ค2569"
    // เรียงตัวยาวกว่าก่อน (มี.ค. ก่อน ม.ค., ก.พ. ก่อน ก.ค.)
    private val thaiMonthAlt =
        """มี\.?\s*ค\.?|ม\.?\s*ค\.?|ก\.?\s*พ\.?|ก\.?\s*ค\.?|ก\.?\s*ย\.?|เม\.?\s*ย\.?|พ\.?\s*ค\.?|มิ\.?\s*ย\.?|ส\.?\s*ค\.?|ต\.?\s*ค\.?|พ\.?\s*ย\.?|ธ\.?\s*ค\.?|มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม|[A-Za-z]{3,9}"""

    private val compactMonthMap = mapOf(
        "มค" to 1, "มกราคม" to 1, "jan" to 1, "january" to 1,
        "กพ" to 2, "กุมภาพันธ์" to 2, "กมภาพนธ" to 2, "feb" to 2, "february" to 2,
        "มีค" to 3, "มีนาคม" to 3, "มนาคม" to 3, "mar" to 3, "march" to 3,
        "เมย" to 4, "เมษายน" to 4, "มษายน" to 4, "apr" to 4, "april" to 4,
        "พค" to 5, "พฤษภาคม" to 5, "may" to 5,
        "มิย" to 6, "มิถุนายน" to 6, "มถนายน" to 6, "jun" to 6, "june" to 6,
        "กค" to 7, "กรกฎาคม" to 7, "jul" to 7, "july" to 7,
        "สค" to 8, "สงหาคม" to 8, "สิงหาคม" to 8, "aug" to 8, "august" to 8,
        "กย" to 9, "กนยายน" to 9, "กันยายน" to 9, "sep" to 9, "sept" to 9, "september" to 9,
        "ตค" to 10, "ตลาคม" to 10, "ตุลาคม" to 10, "oct" to 10, "october" to 10,
        "พย" to 11, "พฤศจกายน" to 11, "พฤศจิกายน" to 11, "nov" to 11, "november" to 11,
        "ธค" to 12, "ธนวาคม" to 12, "ธันวาคม" to 12, "dec" to 12, "december" to 12
    )

    private val textDatePattern = Regex(
        """^(\d{1,2})\s*($thaiMonthAlt)\s*(\d{2,4})$""",
        RegexOption.IGNORE_CASE
    )
    private val slashDatePattern = Regex("""^(\d{1,2})[\/\.-](\d{1,2})[\/\.-](\d{2,4})$""")

    private val dateTimeCombinedRegex = Regex(
        """(\d{1,2}\s*(?:$thaiMonthAlt)\s*\d{2,4}|\d{1,2}[\/\.-]\d{1,2}[\/\.-]\d{2,4})\s*[-,\s]+\s*(\d{1,2}[:.]\d{2}(?::\d{2})?|\d{3,4})(?:\s*น\.?)?""",
        RegexOption.IGNORE_CASE
    )

    private val dateOnlyRegex = Regex(
        """(\d{1,2}\s*(?:$thaiMonthAlt)\s*\d{2,4})|(\d{1,2}[\/\.-]\d{1,2}[\/\.-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )

    // ห้ามใช้ \b กับไทย — ภาษาไทยไม่มีช่องว่างระหว่างคำ ทำให้ \b พัง
    private val timeOnlyRegex = Regex(
        """(?:เวลา|Time|[^0-9]|^)(\d{1,2}[:.]\d{2}(?::\d{2})?(?:\s*น\.?)?)(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    // label ใช้รูปที่ตัดสระแล้ว + รูปเต็ม — จับบนข้อความที่ strip แล้ว
    private val refNoRegex = Regex(
        """(?:รห[สศล]อางอง|เลขทรายการ|เลขทรายการ|รหัสอ้างอิง|เลขที่รายการ|Ref\.?\s*(?:No\.?)?|Transaction\s*ID|Trans\s*ID)\s*[:\s]*([A-Za-z0-9.]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ref1Regex = Regex(
        """(?:เลขอางองท\s*1|เลขทอางอง\s*1|เลขอ้างอิงที่\s*1|เลขที่อ้างอิง\s*1|Ref\.?\s*1)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ref2Regex = Regex(
        """(?:เลขอางองท\s*2|เลขทอางอง\s*2|เลขอ้างอิงที่\s*2|เลขที่อ้างอิง\s*2|Ref\.?\s*2)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ref3Regex = Regex(
        """(?:เลขอางองท\s*3|เลขทอางอง\s*3|เลขอ้างอิงที่\s*3|เลขที่อ้างอิง\s*3|Ref\.?\s*3)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val billerIdRegex = Regex(
        """(?:Biller\s*ID|B1er1D|Bier1D|Bl?er\s*ID|เลขประจำตัวผู้เสียภาษี|บิลเลอร์\s*ไอดี)\s*[:\s]*([0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val merchantCodeRegex = Regex(
        """(?:รหสรานคา|รหัสร้านค้า|Merchant\s*ID|Merchant\s*Code)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val branchCodeRegex = Regex(
        """(?:รหสสาขา|รหัสสาขา|Branch\s*ID|Branch\s*Code)\s*[:\s]*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    // จนวนเงน = จำนวนเงิน ตัดสระ; จบวน = OCR อ่าน น เป็น บ
    private val amountWithLabelRegex = Regex(
        """(?:จา?[นบ]วนเ?งน|จนวน|ยอดเ?งน|ยอดโอน|ยอดรวม|ชาระ|จำนวนเงิน|จำนวน|ยอดเงิน|Amount)\s*[\n\r:\-]*\s*(?:THB|บาท)?\s*([\d,]+(?:\.\d{1,2})?)""",
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
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        
        val lines = normalizedText.lines().map { it.trim().replace(Regex("""\s+"""), " ") }.filter { it.isNotEmpty() }
        val fullText = lines.joinToString("\n")
        // จับวันที่/ยอด/รหัสบนข้อความที่ตัดสระแล้ว — ตัวเลขและชื่ออังกฤษไม่เปลี่ยน
        val matchText = stripThaiMarks(fullText)

        val bankName = detectBankName(fullText)
        // ใช้ทั้งข้อความต้นฉบับและแบบตัดสระ — การตัด ำ ทำให้ลำดับสระหน้า (เ) ไม่ตรงคำเดิม
        val typeSource = fullText + "\n" + matchText
        val transactionType = detectTransactionType(typeSource)
        val direction = detectDirection(typeSource)

        var date: String? = null
        var time: String? = null

        val dateTimeMatch = dateTimeCombinedRegex.find(matchText)
        if (dateTimeMatch != null) {
            date = dateTimeMatch.groupValues[1].trim()
            time = dateTimeMatch.groupValues[2].trim()
        } else {
            val dateMatch = dateOnlyRegex.find(matchText)
            if (dateMatch != null) {
                date = dateMatch.value.trim()
            }
            val timeMatch = timeOnlyRegex.find(matchText)
            if (timeMatch != null) {
                time = timeMatch.groupValues[1].trim()
            }
        }

        val localDateTime = parseToLocalDateTime(date, time)
        val dateTimeIso = localDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val timestampMillis = localDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

        // ref จริงยาว 10+ ตัวเสมอ — ตัวสั้นกว่า 5 (เช่น "oe" จาก "เลขที่รายการ: oe")
        // คือ OCR junk ต้องตัดทิ้ง ไม่งั้น confidence จะ "CONFIRMED" ทั้งที่ ref เพี้ยน
        val refNo = refNoRegex.find(matchText)?.groupValues?.get(1)?.trimEnd('.')
            ?.takeIf { it.length >= 5 }
        val ref1 = ref1Regex.find(matchText)?.groupValues?.get(1)
        val ref2 = ref2Regex.find(matchText)?.groupValues?.get(1)
        val ref3 = ref3Regex.find(matchText)?.groupValues?.get(1)

        val billerId = billerIdRegex.find(matchText)?.groupValues?.get(1)
        val merchantCode = merchantCodeRegex.find(matchText)?.groupValues?.get(1)
        val branchCode = branchCodeRegex.find(matchText)?.groupValues?.get(1)

        val (amount, amountBigDecimal) = extractAmount(matchText)
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
        // 1) ตัดบรรทัดค่าธรรมเนียมออกก่อน — ถ้ายอดจริง OCR พลาด แล้วเหลือแค่
        //    "ค่าธรรมเนียม 0.00 บาท" regex จะจับ 0.00 เป็นคำตอบ (บันทึกยอดผิดเงียบๆ)
        //    ครอบคลุม variants: ค่าธรรมเนียม, คาธรรมเนียม, คารรรมเนียม, ธรรมเนียม, fee
        val amountSource = feeSnippetRegex.replace(fullText, "")

        // 2) PaddleOCR fire space class แทนจุดทศนิยม: "299. 00" → "299.00",
        //    และจุดทศนิยมหายทั้งดวง: "13 00 บาท" ต้องเป็น 13.00 (ไม่ใช่ 1300!)
        //    และคอมม่าแทนจุดทศนิยม: "13,00" -> "13.00"
        //    — แปลง "X YY" (ตามด้วย บาท/THB และไม่มี digit ต่อท้าย) → "X.YY"
        val amountText = amountSource
            .replace(Regex("""(\d+),(\d{2})(?!\d)"""), "$1.$2")           // "13,00" -> "13.00"
            .replace(Regex("""(\d+[\.])\s+(\d{2})"""), "$1$2")            // "299. 00" -> "299.00"
            .replace(Regex("""(\d{1,3})\s+(\d{2})(?!\d)(?=\s*(?:บาท|THB))""", RegexOption.IGNORE_CASE), "$1.$2") // "13 00 บาท" -> "13.00 บาท"
            .replace(Regex("""(?<!\.)\b([\d,]+)(\d{2})(?=\s*(?:บาท|THB))""", RegexOption.IGNORE_CASE), "$1.$2") // "1700 บาท" -> "17.00 บาท" (จุดทศนิยมหาย)
            .replace(Regex("""(?<=\d)\s+(?=\d)"""), "")                    // เหลือ space ระหว่างเลข ("1 300" -> "1300")
        val matches = mutableListOf<MatchResult>()
        amountWithLabelRegex.findAll(amountText).forEach { matches.add(it) }
        amountStandaloneRegex.findAll(amountText).forEach { matches.add(it) }

        // Fallback label regex matching common OCR variations (e.g., ยอดเงิน, ยอดโอน, ชำระ, Total)
        this.fallbackLabelRegex.findAll(amountText).forEach { matches.add(it) }

        if (matches.isNotEmpty()) {
            val bestMatch = matches.sortedBy { match ->
                val labelPos = listOf("จนวน", "จบวน", "จำนวน", "ยอด", "บาท")
                    .map { amountText.indexOf(it, ignoreCase = true) }
                    .firstOrNull { it != -1 } ?: 0
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
        // Trailing (?!\d) instead of \b so OCR noise glued after the amount (e.g. "3166.00um") still matches.
        val candidates = decimalAmountRegex.findAll(amountText).mapNotNull { match ->
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

            val (day, month, rawYear) = when {
                textMatch != null -> Triple(
                    textMatch.groupValues[1].toInt(),
                    resolveThaiMonth(textMatch.groupValues[2]) ?: return null,
                    textMatch.groupValues[3].toInt()
                )
                slashMatch != null -> Triple(
                    slashMatch.groupValues[1].toInt(),
                    slashMatch.groupValues[2].toInt(),
                    slashMatch.groupValues[3].toInt()
                )
                else -> return null
            }

            val parsedDate = resolveSlipDate(day, month, rawYear) ?: return null
            val parsedTime = if (timeStr != null) parseSlipTime(timeStr) ?: return null else LocalTime.MIDNIGHT
            LocalDateTime.of(parsedDate, parsedTime)
        } catch (e: DateTimeException) {
            Log.d(TAG, "Invalid slip date/time date='$dateStr' time='$timeStr'", e)
            null
        } catch (e: NumberFormatException) {
            Log.d(TAG, "Unreadable slip date/time date='$dateStr' time='$timeStr'", e)
            null
        }
    }

    /**
     * แปลงวันจากสลิปผ่าน chronology ของ java.time:
     * - ปี 2 หลัก / ปี พ.ศ. (≥ 2400) → [ThaiBuddhistDate] แล้วค่อยได้ [LocalDate]
     * - ปี ค.ศ. 4 หลัก → [LocalDate.of]
     * วันอธิกสุรทิน (29 ก.พ.) ให้ไลบรารีตัดสิน ไม่บวก/ลบ 543 เอง
     */
    private fun resolveSlipDate(day: Int, month: Int, rawYear: Int): LocalDate? {
        return try {
            val buddhist = when {
                rawYear < 100 -> {
                    val yearOfEra = beTwoDigitYearFormatter
                        .parse(rawYear.toString().padStart(2, '0'))
                        .get(ChronoField.YEAR_OF_ERA)
                    ThaiBuddhistDate.of(yearOfEra, month, day)
                }
                rawYear > 2400 -> ThaiBuddhistDate.of(rawYear, month, day)
                else -> return LocalDate.of(rawYear, month, day)
            }
            LocalDate.from(buddhist)
        } catch (e: DateTimeException) {
            Log.d(TAG, "Invalid calendar date day=$day month=$month year=$rawYear", e)
            null
        }
    }

    private fun parseSlipTime(timeStr: String): LocalTime? {
        val cleaned = timeStr.replace("น.", "").replace("น", "").trim()
        val withColon = cleaned.replace('.', ':')
        val candidates = buildList {
            add(withColon)
            if (cleaned.all { it.isDigit() } && cleaned.length in 3..4) {
                add(cleaned.padStart(4, '0'))
            }
        }
        for (candidate in candidates) {
            val formatters = if (candidate.length == 4 && candidate.all { it.isDigit() }) {
                listOf(slipTimeHHmm)
            } else if (candidate.count { it == ':' } >= 2) {
                listOf(slipTimeHmss, slipTimeHm)
            } else {
                listOf(slipTimeHm, slipTimeHmss)
            }
            for (formatter in formatters) {
                try {
                    return LocalTime.parse(candidate, formatter)
                } catch (_: DateTimeParseException) {
                    continue
                }
            }
        }
        Log.d(TAG, "Unparseable slip time '$timeStr'")
        return null
    }

    /**
     * ตรวจจับธนาคารจากสลิป ชื่อย่อสั้น (SCB, K+, ttb, BAY, GSB, KKP) ตรวจเฉพาะ
     * 8 บรรทัดแรก (ชื่อธนาคารอยู่มุมบนของสลิปเสมอ) เพราะชื่อย่ออาจไปชนกับข้อความอื่น
     * ได้ เช่น "K Plus W" ในชื่อผู้ให้บริการ หรือ "…TTB" ในรหัสธุรกรรม — ตรวจทั้ง
     * ข้อความจะทำให้ bank ผิด (ลงบัญชีผิดธนาคาร) ส่วนชื่อเต็มภาษาไทยเฉพาะเจาะจงพอ
     * จึงตรวจได้ทั้งข้อความ
     */
    internal fun detectBankName(text: String): String? {
        val header = text.lineSequence().take(8).joinToString("\n")
        // ชื่อย่อต้องเป็น token แบบเต็มคำ (มีตัวอักษร/ตัวเลขคั่นหน้า-หลัง) — กัน OCR
        // noise แบบ "MSCB" หรือ "5ttb7" มาชน substring แล้วทำให้ bank ผิด
        fun headerHas(token: String): Boolean =
            Regex("(^|[^A-Za-z0-9])" + Regex.escape(token) + "([^A-Za-z0-9]|$)",
                RegexOption.IGNORE_CASE).containsMatchIn(header)
        val thai = stripThaiMarks(text)
        return when {
            headerHas("SCB") || thai.contains("ไทยพาณชย") -> "SCB"
            headerHas("K+") || headerHas("K PLUS") || headerHas("KBank") ||
                thai.contains("กสกรไทย") -> "KBank"
            headerHas("Krungthai") || thai.contains("กรงไทย") -> "Krungthai"
            headerHas("ttb") || thai.contains("ทหารไทยธนชาต") -> "ttb"
            headerHas("Bangkok Bank") || thai.contains("กรงเทพ") -> "Bangkok Bank"
            headerHas("BAY") || thai.contains("กรงศร") -> "Krungsri"
            headerHas("KKP") || thai.contains("เกยรตนาคน") -> "KKP"
            headerHas("GSB") || thai.contains("ออมสน") -> "GSB"
            else -> null
        }
    }

    private fun detectTransactionType(text: String): String? {
        return when {
            text.contains("จายบล") || text.contains("ชาระเงน") || text.contains("ชาระสนคา") ||
                text.contains("บลเลอร") || text.contains("Biller", ignoreCase = true) -> "จ่ายบิลสำเร็จ"
            text.contains("โอนเงนเสรจ") || text.contains("โอนเสรจ") || text.contains("โอนเงินสำเร็จ") ||
                text.contains("โอนสำเร็จ") -> "โอนเงินสำเร็จ"
            text.contains("รบเงน") || text.contains("เงนเขา") || text.contains("รับเงินสำเร็จ") ||
                text.contains("เงินเข้า") -> "รับเงินสำเร็จ"
            text.contains("เตมเงน") || text.contains("เติมเงินสำเร็จ") -> "เติมเงินสำเร็จ"
            else -> "รายการสำเร็จ"
        }
    }

    private fun detectDirection(text: String): SlipDirection {
        val t = text
        return when {
            t.contains("จายบล") || t.contains("ง่ายบล") || t.contains("จ่ายบิล") ||
                t.contains("ชาระเงน") || t.contains("ชำระเงิน") || t.contains("ชาระสนคา") ||
                t.contains("ชำระสินค้า") || t.contains("Biller", ignoreCase = true) ||
                t.contains("Bill Payment", ignoreCase = true) -> SlipDirection.BILL_PAYMENT
            t.contains("รบเงน") || t.contains("รับเงิน") || t.contains("เงนเขา") || t.contains("เงินเข้า") ||
                t.contains("โอนเงนเขา") || t.contains("โอนเงินเข้า") || t.contains("รับโอน") ||
                t.contains("Received", ignoreCase = true) -> SlipDirection.INCOMING
            t.contains("โอนเงน") || t.contains("โอนเงิน") || t.contains("โอนเสรจ") || t.contains("โอนสเรจ") ||
                t.contains("โอนสำเร็จ") || t.contains("Transfer Success", ignoreCase = true) -> SlipDirection.OUTGOING
            else -> SlipDirection.UNKNOWN
        }
    }

    internal fun stripThaiMarks(text: String): String = thaiMarksRegex.replace(text, "")

    private fun resolveThaiMonth(raw: String): Int? {
        val compact = raw.trim().lowercase()
            .replace(".", "")
            .replace(" ", "")
        compactMonthMap[compact]?.let { return it }
        compactMonthMap[stripThaiMarks(compact)]?.let { return it }
        return null
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
            val stripped = stripThaiMarks(line)
            line.contains("ไปยัง") || stripped.contains("ไปยง") ||
                stripped.contains("โอนให") || line.equals("To", ignoreCase = true)
        }

        val fromIndex = lines.indexOfFirst { line ->
            val stripped = stripThaiMarks(line)
            stripped.contains("จาก") || line.equals("From", ignoreCase = true)
        }

        // OCR มักรวมคำว่า "ไปยัง"/"จาก" กับชื่อไว้ในบรรทัดเดียวกัน
        // (เช่น "ไปยัง ร้านถุงเงิน (ต่อพลาสติก)") — แยกชื่อที่อยู่หลัง keyword ออกมา
        val inlineReceiverName = toIndex.takeIf { it != -1 }
            ?.let { extractInlineName(lines[it], listOf("ไปยัง", "ไปยง", "โอนให้", "โอนให")) }
        val inlineSenderName = fromIndex.takeIf { it != -1 }
            ?.let { extractInlineName(lines[it], listOf("จาก")) }

        if (toIndex != -1) {
            // ชื่อที่รวมกับ "ไปยัง" ในบรรทัดเดียว (OCR merge) มีน้ำหนักมากกว่าชื่อ
            // junk ที่ loop ด้านล่างอาจเจอ (เช่น "จาบวนเงิน299.00")
            if (inlineReceiverName != null) {
                receiverName = inlineReceiverName
            }
            if (inlineSenderName != null) {
                senderName = inlineSenderName
            }

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
                val stripped = stripThaiMarks(line)
                stripped.contains("จนวน") || stripped.contains("จบวน") ||
                    line.contains("จำนวนเงิน") || line.contains("Amount", ignoreCase = true)
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
            // กรณีไม่มีคำว่า "ไปยัง" — ใช้ชื่อที่รวมกับ "จาก" ในบรรทัดเดียวเป็นตัวตั้งต้น
            if (inlineSenderName != null) {
                senderName = inlineSenderName
            }

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
                    val cleanLine = line.replace(Regex("""^[\<\>\-\=\:\s]+"""), "")
                    // Priority check: If it has a prefix like "นาย", it's a very strong candidate
                    val hasNamePrefix = personOrgPrefixes.any { cleanLine.startsWith(it, ignoreCase = true) }
                    
                    val cleanSender = senderName?.replace(Regex("""^[\<\>\-\=\:\s]+"""), "") ?: ""
                    val senderHasPrefix = personOrgPrefixes.any { cleanSender.startsWith(it, ignoreCase = true) }
                    
                    val cleanReceiver = receiverName?.replace(Regex("""^[\<\>\-\=\:\s]+"""), "") ?: ""
                    val receiverHasPrefix = personOrgPrefixes.any { cleanReceiver.startsWith(it, ignoreCase = true) }

                    if (senderName == null) {
                        senderName = line
                    } else if (receiverName == null && line != senderName) {
                        if (!senderHasPrefix && hasNamePrefix) {
                            senderName = line
                        } else {
                            receiverName = line
                        }
                    } else if (receiverName != null && line != senderName && line != receiverName) {
                        if (!senderHasPrefix && hasNamePrefix) {
                            senderName = line
                        } else if (!receiverHasPrefix && hasNamePrefix) {
                            receiverName = line
                        }
                    }
                } else if (accountNoRegex.matches(line) || line.contains("xxx", ignoreCase = true)) {
                    if (senderAccount == null) senderAccount = line
                    else if (receiverAccount == null) receiverAccount = line
                }
            }
        }

        return PartyInfo(senderName, senderAccount, receiverName, receiverAccount)
    }

    /**
     * แยกชื่อที่อยู่หลัง keyword ในบรรทัดเดียวกัน (เช่น "ไปยัง ร้านถุงเงิน (ต่อพลาสติก)"
     * หรือ "จาก นายสมชาย") — เกิดบ่อยกับ OCR ที่รวมหลายบรรทัดเป็นบรรทัดเดียว
     */
    private fun extractInlineName(line: String, keywords: List<String>): String? {
        val keyword = keywords.firstOrNull { needle ->
            line.contains(needle, ignoreCase = true) || stripThaiMarks(line).contains(needle, ignoreCase = true)
        } ?: return null
        val name = when {
            line.contains(keyword, ignoreCase = true) -> line.substringAfter(keyword, "")
            else -> {
                val stripped = stripThaiMarks(line)
                val idx = stripped.indexOf(keyword, ignoreCase = true)
                if (idx < 0) "" else stripped.substring(idx + keyword.length)
            }
        }.trim()
        if (name.isEmpty() || name.length < 3) return null
        // ตัด junk ที่ OCR มักแปะติดหน้า/ท้ายชื่อ (เครื่องหมาย, ตัวอักษรเดี่ยว, ฯลฯ)
        val cleaned = cleanNameString(name.trimStart('@', '-', '.', ':', ' '))
        if (cleaned == null || cleaned.length < 3) return null
        // กันกรณี "ไปยัง XXX-1234" — ค่าที่เป็นเลขบัญชีไม่ใช่ชื่อ ต้องปล่อยให้ loop
        // ไปเจอชื่อจริงในบรรทัดถัดไป
        if (accountNoRegex.matches(cleaned) || cleaned.contains("xxx", ignoreCase = true)) {
            return null
        }
        return if (isValidNameCandidate(cleaned)) cleaned else null
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
        
        // ลบอักขระขยะหน้าชื่อ (เช่น <, >, -, =, :)
        cleaned = cleaned.replace(Regex("""^[\<\>\-\=\:\s]+"""), "")
        
        // 1. ลบชื่อธนาคารออกจากชื่อ (ตรรกะเดิมของคุณ)
        val bankSuffixRegex = Regex("""\s*(?:ธนาคาร|ธ\.)\s*[\u0E00-\u0E7F]+(?=$|[^\u0E00-\u0E7F])""")
        cleaned = cleaned.replace(bankSuffixRegex, "")
        
        // 2. ลบตัวอักษรขยะท้ายชื่อที่ OCR มักจะอ่านผิด (เช่น 'a', 'อ', '.')
        cleaned = cleaned.trim()
            .replace(Regex("""\s+[a-zA-Z]$"""), "") // ลบตัวอังกฤษตัวเดียวท้ายชื่อ
            .replace(Regex("""\s+[\u0E00-\u0E7F]$"""), "") // ลบตัวไทยตัวเดียวท้ายชื่อ
            .replace(Regex("""\.$"""), "") // ลบจุดท้ายชื่อ
            
        cleaned = cleaned.trim()
        return if (cleaned.isEmpty()) null else cleaned
    }

    private fun isBankNameLine(line: String): Boolean {
        val trimmed = line.trim()
        return bankNamesList.any { trimmed.equals(it, ignoreCase = true) || trimmed.startsWith("ธ.") || trimmed.startsWith("ธนาคาร") }
    }

    private fun isValidNameCandidate(line: String): Boolean {
        if (line.length < 3) return false
        if (knownAppNames.any {
                line.equals(it, ignoreCase = true) ||
                    line.trimEnd('.', ',', ';', ':').equals(it, ignoreCase = true)
            }) return false
        if (isBankNameLine(line)) return false

        // ชื่อต้องมีตัวอักษรจริง (ไทย/อังกฤษ) อย่างน้อย 2 ตัว — ตัด junk เช่น "4--",
        // "1ศห--" ที่ OCR อ่านเละแล้วเหลือแต่ตัวเลข/เครื่องหมาย
        val letterCount = line.count { it.isLetter() }
        if (letterCount < 2) return false

        if (junkNameKeywords.any { line.contains(it) }) return false

        if (line.contains("รหัสอ้างอิง") || 
            line.contains("เลขที่รายการ") || 
            line.contains("จำนวน:") || 
            line.contains("ผู้รับเงินสามารถ")) return false

        if (labelPrefixes.any { line.startsWith(it, ignoreCase = true) }) return false
        if (dateOnlyRegex.containsMatchIn(line) || timeOnlyRegex.containsMatchIn(line)) return false

        val cleanLine = line.replace(Regex("""^[\<\>\-\=\:\s]+"""), "")
        if (namePrefixes.any { cleanLine.startsWith(it, ignoreCase = true) }) return true

        val uppercaseCompanyPattern = Regex("""^[A-Z0-9\s\.\&\-]{3,}$""")
        if (uppercaseCompanyPattern.matches(cleanLine) && cleanLine.length >= 3) return true

        val namePattern = Regex("""^[\u0E00-\u0E7FA-Za-z0-9\s\(\)\.\/\&\–-]{3,}$""")
        return namePattern.matches(cleanLine)
    }

    private fun isSkipLine(line: String): Boolean {
        if (knownAppNames.any {
                line.equals(it, ignoreCase = true) ||
                    line.trimEnd('.', ',', ';', ':').equals(it, ignoreCase = true)
            }) return true

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
