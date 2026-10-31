package com.pennywiseai.tracker.slip.parser

import android.util.Log
import java.math.BigDecimal
import java.text.Normalizer
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.ThaiBuddhistDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
    val receiverNameRecovered: Boolean = false,
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

    // ลบสัญลักษณ์และตัวเลขขยะหน้าชื่อ (รวมถึงเลขไทย ๑-๙ ที่ชอบติดมาหน้าชื่อโอน)
    private val leadingJunkRegex = Regex("""^[\<\>\-\=\:\s\.\,๑๒๓๔๕๖๗๘๙๐\(\)\[\]|]+""")

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
        "พรบ", "ลง", "สลิป", "รายการ", "ตรวจสอบ", "สำเร็จ", "สําเร็จ",
        "จํานวนเงิน", "จำนวนเงิน", "ค่าธรรมเนียม", "ผู้รับเงินสามารถ", "ฟูรับเงินสามารถ", "ผูรับเงิน",
        "ชำระเงิน", "ชําระเงิน", "โอนเงิน", "โอนเสร็จ", "เติมเงิน", "เต็มเงิน",
        "ข้อมูลเพิ่มเติม", "ผู้ให้บริการ", "จากผู้ให้บริการ", "ตรวจสอบสถานะ",
        "คิวอาร์โค้ด", "คิวอาร์", "QR Code",
        "รหัสพร้อมเพย์", "พร้อมเพย์", "PromptPay", "สแกนตรวจสอบ", "สแกนตรวจสอบสลิป"
    )

    private val labelPrefixes = listOf(
        "Biller ID", "รหัสร้านค้า", "รหัสสาขา", "รหัสธุรกรรม",
        "เลขที่อ้างอิง", "เลขอ้างอิง", "ค่าธรรมเนียม", "Ref", "Trans", "พรบ",
        "เลขอางอง", "รหสอางอง", "เลขทรายการ", "เลขทอางอง", // Stripped variants
        "รหัสพร้อมเพย์", "พร้อมเพย์", "PromptPay", "สแกนตรวจสอบ",
        "ข้อมูลเพิ่มเติม", "จากผู้ให้บริการ", "ตรวจสอบสถานะ"
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

    // สระ/วรรณยุกต์ที่อยู่บน-ล่างพยัญชนะ — OCR มักตกหล่น หรือแยกส่วนมา (เช่น สระอำแยกเป็น นิคหิต + สระอา)
    // \u0E4D = นิคหิต (ํ), \u0E33 = สระอำ (ำ), \u0E4C = การันต์ (์)
    private val thaiMarksRegex = Regex("""[\u0E31\u0E33-\u0E3A\u0E47-\u0E4E\u0E4D]""")

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

    // ปรับให้เข้มงวดขึ้น: เลขบัญชีต้องมี x หรือ - หรือช่องว่างคั่น 
    // ไม่งั้นจะไปจับเอารหัสอ้างอิง (Ref No) ยาวๆ มาเป็นเลขบัญชีแล้วเหลือเศษเป็นชื่อ (เช่น JPT)
    private val accountNoRegex = Regex(
        """^(?=.*[xX\- ])[xX\d]{3,4}[-  ]*[xX\d]{1,4}[-  ]*[xX\d]{3,5}[-  ]*[xX\d]{0,4}$"""
    )

    private val inlineAccountNoRegex = Regex(
        """([xX\d]{3,4}[-  ]+[xX\d]{1,4}[-  ]+[xX\d]{3,5}|[xX]{2,}[xX\d]+)"""
    )

    private val knownAppNames = listOf(
        "SCB+", "SCB", "K+", "K PLUS", "KPLUS", "KBank", "Krungthai NEXT", "Krungthai", "ttb touch", "ttb", 
        "MyMo", "GSB", "BAY", "Krungsri", "Bangkok Bank", "Bualuang MBANK", "VISA", "MASTERCARD"
    )

    private val bankNamesList = listOf(
        "ธ.กสิกรไทย", "ธนาคารกสิกรไทย", "กสิกรไทย", "KBank", "K-Bank",
        "ธ.ไทยพาณิชย์", "ธนาคารไทยพาณิชย์", "ไทยพาณิชย์", "SCB",
        "ธ.กรุงไทย", "ธนาคารกรุงไทย", "กรุงไทย", "Krungthai", "KTB",
        "ธ.กรุงเทพ", "ธนาคารกรุงเทพ", "กรุงเทพ", "Bangkok Bank", "BBL",
        "ธ.กรุงศรี", "ธนาคารกรุงศรีอยุธยา", "กรุงศรีอยุธยา", "Krungsri", "BAY",
        "ธ.ออมสิน", "ธนาคารออมสิน", "ออมสิน", "GSB",
        "ธ.ทหารไทยธนชาต", "ธนาคารทหารไทยธนชาต", "ttb", "TMB",
        "ธ.เกียรตินาคิน", "ธนาคารเกียรตินาคินภัทร", "KKP",
        "ธ.ก.ส.", "ธนาคารเพื่อการเกษตรและสหกรณ์การเกษตร", "BAAC",
        "ธ.อาคารสงเคราะห์", "GHB", "UOB", "CIMB", "LHB", "Standard Chartered"
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
        
        // 🔄 Recovery Strategy: ถ้าชื่อผู้รับเป็นแค่ Generic PromptPay → ลองกู้ชื่อจริงจากส่วนล่างของสลิป
        val recoveredReceiver = recoverPromptPayRecipient(lines, receiverName)
        val finalReceiverName = recoveredReceiver ?: receiverName
        
        Log.d(TAG, "Auto-parse result: Sender=$senderName, Receiver=$finalReceiverName (Recovered=${recoveredReceiver != null}), Amount=$amountBigDecimal")

        val confidence = evaluateConfidence(
            bankName = bankName,
            amount = amountBigDecimal,
            date = localDateTime,
            refNo = refNo,
            senderName = senderName,
            receiverName = finalReceiverName,
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
            receiverName = cleanNameString(finalReceiverName),
            receiverAccount = receiverAccount,
            receiverNameRecovered = recoveredReceiver != null,
            billerId = billerId,
            merchantCode = merchantCode,
            branchCode = branchCode,
            amount = amount,
            amountBigDecimal = amountBigDecimal,
            rawText = fullText
        )
    }

    /**
     * Recovery สำหรับสลิปที่ชื่อผู้รับเป็น generic keyword 
     * เช่น "เติมเงินพร้อมเพย์", "โอนเงินพร้อมเพย์", "รับเงินพร้อมเพย์"
     * จะมองหาชื่อจริงในส่วน "ข้อมูลเพิ่มเติมจากผู้ให้บริการ" แทน
     */
    private fun recoverPromptPayRecipient(lines: List<String>, parsedReceiver: String?): String? {
        val promptPayKeywords = listOf(
            "เติมเงิน", "เต็มเงิน", "โอนเงิน", "รับเงิน", "พร้อมเพย์", "พรอมเพย", 
            "e-Wallet", "Wallet", "PromptPay", "Top up", "Top-up"
        )
        
        val isGenericPromptPay = parsedReceiver?.let { name ->
            val cleaned = stripThaiMarks(name.lowercase())
            promptPayKeywords.any { cleaned.contains(stripThaiMarks(it.lowercase())) }
        } ?: false
        
        if (!isGenericPromptPay) return null
        
        Log.d(TAG, "🔄 PromptPay recovery triggered for: '$parsedReceiver'")
        
        // หา anchor "ข้อมูลเพิ่มเติมจากผู้ให้บริการ" (รองรับ OCR variants และภาษาอังกฤษ)
        val anchorIndex = lines.indexOfFirst { line ->
            val stripped = stripThaiMarks(line)
            line.contains("ข้อมูลเพิ่มเติม") || stripped.contains("ขอมูลเพิมเตม") ||
                stripped.contains("ขอมลเพมเตม") || 
                line.contains("Additional Info", ignoreCase = true) ||
                line.contains("Memo", ignoreCase = true) ||
                line.contains("Note", ignoreCase = true)
        }
        
        if (anchorIndex == -1) {
            Log.d(TAG, "⚠️ Recovery anchor not found")
            return null
        }
        
        // สแกน 4 บรรทัดถัดจาก anchor เพื่อหาชื่อจริง
        for (i in (anchorIndex + 1)..minOf(anchorIndex + 4, lines.lastIndex)) {
            val line = lines[i]
            val cleaned = cleanNameString(getCleanedLine(line))
            
            if (cleaned != null && isValidNameCandidate(line) && !isBankNameLine(line)) {
                // ต้องไม่ใช่ขยะที่พบบ่อยในโซนนี้ (รองรับภาษาอังกฤษ)
                val skipWords = listOf(
                    "สแกน", "คิวอาร์", "ตรวจสอบ", "สถานะ", "ผู้รับเงินสามารถ", "Biller",
                    "Scan", "QR", "Verify", "Status", "Receiver", "Sender"
                )
                if (skipWords.none { line.contains(it, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Recovery success: '$cleaned' (from line $i)")
                    return cleaned
                }
            }
        }
        
        Log.d(TAG, "⚠️ Recovery failed: no valid name found after anchor")
        return null
    }

    private fun extractAmount(fullText: String): Pair<Double?, BigDecimal?> {
        // 1) ตัดบรรทัดค่าธรรมเนียมออกก่อน — ถ้ายอดจริง OCR พลาด แล้วเหลือแค่
        //    "ค่าธรรมเนียม 0.00 บาท" regex จะจับ 0.00 เป็นคำตอบ (บันทึกยอดผิดเงียบๆ)
        //    ครอบคลุม variants: ค่าธรรมเนียม, คาธรรมเนียม, คารรรมเนียม, ธรรมเนียม, fee
        val amountSource = feeSnippetRegex.replace(fullText, "")

        // 2) ระบบซ่อมจุดทศนิยมที่ OCR อ่านไม่เจอ — สลิปธนาคารไทยทุกใบลงท้าย ".00" เสมอ
        //    ดังนั้นถ้า OCR อ่านจุด/ทศนิยมหาย เรารู้ได้ว่าต้องเติมจุดกลับ:
        //    • "13,00" (คอมม่าแทนจุด)     -> "13.00"
        //    • "299. 00" (Paddle อ่านช่องว่างแทนจุด) -> "299.00"
        //    • "13 00 บาท" (จุดหายทั้งดวง) -> "13.00 บาท" (ไม่ใช่ 1300!)
        //    • "1700 บาท" (จุดหายทั้งดวง)  -> "17.00 บาท" — ตัวเลข 4 หลักติดกันตามด้วย บาท
        //      (?<!\d ) กันเลขหลักพันแบบมีช่องว่าง: "1 300 บาท" = 1,300 บาท (ไม่ใช่ 13.00)
        val amountText = amountSource
            .replace(Regex("""(\d+),(\d{2})(?!\d)"""), "$1.$2")           // "13,00" -> "13.00"
            .replace(Regex("""(\d+[\.])\s+(\d{2})"""), "$1$2")            // "299. 00" -> "299.00"
            .replace(Regex("""(\d{1,3})\s+(\d{2})(?!\d)(?=\s*(?:บาท|THB))""", RegexOption.IGNORE_CASE), "$1.$2") // "13 00 บาท" -> "13.00 บาท"
            .replace(Regex("""(?<![,\.\d])(\d{2})(\d{2})(?=\s*(?:บาท|THB))""", RegexOption.IGNORE_CASE), "$1.$2") // "1700 บาท" (4 หลัก ไม่มีจุด ไม่มี comma) -> "17.00 บาท" — ป้องกัน OCR อ่านจุดตก; ไม่ตัด "1,500" หรือ "500" หรือ "100" บาท
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

            // สลิปภาษาอังกฤษ (เช่น "15 Aug 26") — ปี 2 หลักเป็น ค.ศ. (2026)
            // ไม่ใช่ พ.ศ. เหมือนสลิปไทย ("11 ส.ค. 69" = พ.ศ. 2569)
            val englishYear = textMatch?.groupValues?.get(2)?.matches(Regex("[A-Za-z]{3,9}")) == true

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

            val parsedDate = resolveSlipDate(day, month, rawYear, englishYear) ?: return null
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
    private fun resolveSlipDate(day: Int, month: Int, rawYear: Int, englishYear: Boolean = false): LocalDate? {
        return try {
            // สลิปภาษาอังกฤษปี 2 หลัก (เช่น "15 Aug 26") → ค.ศ. 20xx ไม่ใช่ พ.ศ.
            if (englishYear && rawYear < 100) {
                return LocalDate.of(2000 + rawYear, month, day)
            }
            val buddhist = when {
                rawYear < 100 -> {
                    // ปี พ.ศ. 2 หลัก เช่น "68" = พ.ศ. 2568 — ใช้ฐานคงที่ 2500 (ยุคปัจจุบัน
                    // ของสลิปไทย) ไม่ใช้ reduced year ที่ base = ปีปัจจุบัน เพราะ window
                    // เลื่อนตามปี (ตอนนี้ "68" -> 2668 = ค.ศ. 2125 ผิดไป 100 ปี)
                    val yearOfEra = 2500 + rawYear
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
                text.contains("บลเลอร") || text.contains("Biller", ignoreCase = true) ||
                text.contains("สแกนจาย") || text.contains("สแกนจ่าย") ||
                text.contains("QR Payment", ignoreCase = true) || text.contains("Thai QR", ignoreCase = true) -> "จ่ายบิลสำเร็จ"
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
        // True INCOMING: สลิปเงินเข้า เช่น "รับเงินสำเร็จ", "เงินเข้า", "โอนเงินเข้า", "รับโอน", "Received"
        val hasIncomingSignal = t.contains("รบเงนสาเรจ") || t.contains("รับเงินสำเร็จ") ||
            t.contains("เงนเขา") || t.contains("เงินเข้า") ||
            t.contains("โอนเงนเขา") || t.contains("โอนเงินเข้า") ||
            t.contains("รับโอน") || t.contains("Received", ignoreCase = true)

        // OUTGOING: สลิปโอนเงินออก หรือ เติมเงิน (โดยต้องไม่ใช่สลิปเงินเข้า)
        val hasOutgoingSignal = (t.contains("โอนเงน") || t.contains("โอนเงิน") ||
            t.contains("โอนเสรจ") || t.contains("โอนสเรจ") ||
            t.contains("โอนสำเร็จ") || t.contains("Transfer Success", ignoreCase = true) ||
            t.contains("เตมเงน") || t.contains("เติมเงิน") || t.contains("เติมเงินสำเร็จ") ||
            t.contains("Top up", ignoreCase = true) || t.contains("Top-up", ignoreCase = true) ||
            t.contains("Topup", ignoreCase = true)) && !hasIncomingSignal

        return when {
            t.contains("จายบล") || t.contains("ง่ายบล") || t.contains("จ่ายบิล") ||
                t.contains("ชาระเงน") || t.contains("ชำระเงิน") || t.contains("ชาระสนคา") ||
                t.contains("ชำระสินค้า") || t.contains("Biller", ignoreCase = true) ||
                t.contains("Bill Payment", ignoreCase = true) ||
                t.contains("สแกนจาย") || t.contains("สแกนจ่าย") ||
                t.contains("QR Payment", ignoreCase = true) || t.contains("Thai QR", ignoreCase = true) ||
                t.contains("PromptPay QR", ignoreCase = true) -> SlipDirection.BILL_PAYMENT
            hasIncomingSignal -> SlipDirection.INCOMING
            hasOutgoingSignal -> SlipDirection.OUTGOING
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

        // OCR มักอ่านปี/วันที่เพี้ยนเป็นอนาคต (เช่น อ่านเลขปีผิด หรือเลือก era ผิด)
        // บังคับ NEEDS_REVIEW เพื่อให้ผู้ใช้ตรวจสอบก่อนบันทึก — เผื่อ tolerance 2 วัน
        // กันความเพี้ยนของนาฬิกาเครื่อง / timezone เล็กน้อย
        val futureThreshold = LocalDateTime.now().plus(Duration.ofDays(2))
        if (date.isAfter(futureThreshold)) {
            return SlipConfidence.NEEDS_REVIEW
        }

        val hasValidReceiver = !receiverName.isNullOrBlank() && receiverName.length >= 3
        val hasValidSender = !senderName.isNullOrBlank() && senderName.length >= 3

        val isConfirmed = when (direction) {
            SlipDirection.BILL_PAYMENT -> billerId != null || hasValidReceiver
            SlipDirection.INCOMING -> hasValidSender
            SlipDirection.OUTGOING -> hasValidReceiver
            SlipDirection.UNKNOWN -> hasValidReceiver && hasValidSender
        }

        return when {
            refNo != null && isConfirmed -> SlipConfidence.CONFIRMED
            hasValidReceiver || hasValidSender -> SlipConfidence.HIGH
            else -> SlipConfidence.NEEDS_REVIEW
        }
    }

    private fun isAccountOrWalletNumber(line: String): Boolean {
        val trimmed = line.trim()
        return accountNoRegex.matches(trimmed) ||
            trimmed.matches(Regex("""^\d{10,18}$""")) ||
            (trimmed.contains("xxx", ignoreCase = true) && !trimmed.any { it in '\u0E00'..'\u0E7F' })
    }

    private fun extractParties(lines: List<String>): PartyInfo {
        var senderName: String? = null
        var senderAccount: String? = null
        var receiverName: String? = null
        var receiverAccount: String? = null

        val toIndex = lines.indexOfFirst { line ->
            val stripped = stripThaiMarks(line)
            line.contains("ไปยัง") || stripped.contains("ไปยง") ||
                stripped.contains("โอนให้") || stripped.contains("โอนให") || line.equals("To", ignoreCase = true)
        }

        val fromIndex = lines.indexOfFirst { line ->
            val stripped = stripThaiMarks(line)
            stripped.contains("จาก") || line.equals("From", ignoreCase = true)
        }

        // OCR มักรวมคำว่า "ไปยัง"/"จาก" กับชื่อไว้ในบรรทัดเดียวกัน
        val inlineReceiverName = toIndex.takeIf { it != -1 }
            ?.let { extractInlineName(lines[it], listOf("ไปยัง", "ไปยง", "โอนให้", "โอนให")) }
        val inlineSenderName = fromIndex.takeIf { it != -1 }
            ?.let { extractInlineName(lines[it], listOf("จาก")) }

        if (toIndex != -1) {
            if (inlineReceiverName != null) receiverName = inlineReceiverName
            if (inlineSenderName != null) senderName = inlineSenderName

            val senderStart = if (fromIndex != -1) fromIndex + 1 else 0
            val senderEnd = toIndex

            for (i in senderStart until senderEnd) {
                val line = lines[i]
                if (isSkipLine(line) || isBankNameLine(line)) continue

                if (isAccountOrWalletNumber(line)) {
                    if (senderAccount == null) senderAccount = line
                    continue
                }

                val (namePart, accPart) = splitNameAndAccount(line)
                if (accPart != null && senderAccount == null) senderAccount = accPart
                
                val candidateText = namePart ?: line
                val cleaned = cleanNameString(getCleanedLine(candidateText))
                if (cleaned != null && isValidNameCandidate(cleaned) && senderName == null) {
                    senderName = cleaned
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

                if (isAccountOrWalletNumber(line)) {
                    if (receiverAccount == null) receiverAccount = line
                    continue
                }

                val (namePart, accPart) = splitNameAndAccount(line)
                if (accPart != null && receiverAccount == null) receiverAccount = accPart
                
                val candidateText = namePart ?: line
                val cleaned = cleanNameString(getCleanedLine(candidateText))
                if (cleaned != null && isValidNameCandidate(cleaned) && receiverName == null) {
                    receiverName = cleaned
                }
            }

            // If receiverName is still not found (e.g. SCB Top-Up / PromptPay with additional provider info below amount)
            if (receiverName == null) {
                val scanStart = if (amountIndex != -1 && amountIndex > toIndex) amountIndex + 1 else toIndex + 1
                val scanEnd = minOf(scanStart + 8, lines.size)
                for (i in scanStart until scanEnd) {
                    val line = lines[i]
                    if (isSkipLine(line) || isBankNameLine(line)) continue

                    if (isAccountOrWalletNumber(line)) {
                        if (receiverAccount == null) receiverAccount = line
                        continue
                    }

                    val (namePart, accPart) = splitNameAndAccount(line)
                    if (accPart != null && receiverAccount == null) receiverAccount = accPart
                    
                    val candidateText = namePart ?: line
                    val cleaned = cleanNameString(getCleanedLine(candidateText))
                    if (cleaned != null && isValidNameCandidate(cleaned) && receiverName == null) {
                        receiverName = cleaned
                    }
                }
            }

        } else {
            if (inlineSenderName != null) senderName = inlineSenderName

            for (line in lines) {
                if (isSkipLine(line) || isBankNameLine(line)) continue

                if (accountNoRegex.matches(line) || (line.contains("xxx", ignoreCase = true) && !line.any { it in '\u0E00'..'\u0E7F' })) {
                    if (senderAccount == null) senderAccount = line
                    else if (receiverAccount == null) receiverAccount = line
                    continue
                }

                val (namePart, accPart) = splitNameAndAccount(line)
                if (accPart != null) {
                    if (senderAccount == null) senderAccount = accPart
                    else if (receiverAccount == null) receiverAccount = accPart
                }

                val candidateText = namePart ?: line
                val cleaned = cleanNameString(getCleanedLine(candidateText))
                if (cleaned != null && isValidNameCandidate(cleaned)) {
                    if (senderName == null) {
                        senderName = cleaned
                    } else if (receiverName == null && cleaned != senderName) {
                        receiverName = cleaned
                    }
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
            
            // ป้องกัน Account Regex กิน Ref Code จนเหลือเศษเป็นชื่อสั้นๆ เช่น JPT
            val candidate = getCleanedLine(namePart)
            if (candidate.isNotEmpty() && candidate.length <= 5 
                && !candidate.any { it in '\u0E00'..'\u0E7F' }
                && candidate.all { it.isUpperCase() || it.isDigit() }) {
                Log.d(TAG, "Rejecting split: '$line' -> leftover '$candidate' looks like ref code fragment")
                return Pair(null, acc)
            }
            
            val cleanName = if (namePart.length >= 3 && isValidNameCandidate(namePart)) namePart else null
            return Pair(cleanName, acc)
        }
        return Pair(null, null)
    }

    private fun cleanNameString(name: String?): String? {
        if (name == null) return null
        var cleaned = name

        // ลบอักขระขยะหน้าชื่อ (เช่น @, <, >, -, =, :, .)
        cleaned = cleaned.replace(Regex("""^[@\s\<\>\-\=\:\.\,]+"""), "")

        // ลบ prefix อังกฤษสั้น 1-4 ตัว ที่ OCR อ่านผิดจากคำนำหน้าชื่อไทย
        // เช่น "wie ปฐวิกรณ์" (OCR อ่าน "นาย" ผิด), "nay สมชาย", "wle", "wai", "uns", "wa"
        // ทำเฉพาะเมื่อมีตัวอักษรไทยอยู่ในชื่อด้วย ป้องกันลบชื่อฝรั่งที่ถูกต้อง
        if (cleaned.any { it in '\u0E00'..'\u0E7F' }) {
            cleaned = cleaned.replace(Regex("""^(?:wie|wle|wai|nay|uns|wa|[a-zA-Z]{1,4})\.?\s+""", RegexOption.IGNORE_CASE), "")
        }

        // 1. ลบชื่อธนาคารออกจากชื่อ
        val bankSuffixRegex = Regex("""\s*(?:ธนาคาร|ธ\.)\s*[\u0E00-\u0E7F]+(?=$|[^\u0E00-\u0E7F])""")
        cleaned = cleaned.replace(bankSuffixRegex, "")

        // 2. ลบตัวอักษรขยะท้ายชื่อที่ OCR มักจะอ่านผิด (เช่น ' a', ' จ', ตัวเลขไทยขยะ)
        cleaned = cleaned.trim()
            .replace(Regex("""\s+[a-zA-Z]$"""), "") // ตัวอังกฤษตัวเดียวท้ายชื่อ
            .replace(Regex("""\s+[\u0E00-\u0E7F]$"""), "") // ตัวไทยตัวเดียวท้ายชื่อ
            .replace(Regex("""\s+[\u0E50-\u0E59]+$"""), "") // ตัวเลขไทยขยะท้ายชื่อ
            .replace(Regex("""[.\s,<>:\-]+$"""), "") // จุดและสัญลักษณ์ท้ายชื่อ

        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return if (cleaned.isEmpty()) null else cleaned
    }

    private fun isBankNameLine(line: String): Boolean {
        val trimmed = line.trim()
        val hasBankKeyword = trimmed.contains("Bank", ignoreCase = true) || 
                            trimmed.contains("ธนาคาร", ignoreCase = true) ||
                            trimmed.startsWith("ธ.")
        return hasBankKeyword || bankNamesList.any { trimmed.equals(it, ignoreCase = true) }
    }

    private fun getCleanedLine(line: String): String = line.replace(leadingJunkRegex, "").trim()

    private fun isValidNameCandidate(line: String): Boolean {
        val candidate = getCleanedLine(line)
        if (candidate.length < 3) return false
        
        if (knownAppNames.any {
                candidate.equals(it, ignoreCase = true) ||
                    candidate.trimEnd('.', ',', ';', ':').equals(it, ignoreCase = true)
            }) return false
        if (isBankNameLine(candidate)) return false

        // ชื่อต้องมีตัวอักษรจริง (ไทย/อังกฤษ) อย่างน้อย 2 ตัว
        val letterCount = candidate.count { it.isLetter() }
        if (letterCount < 2) return false

        // ดัก alphanumeric codes (ตัวอย่าง: JPT2026..., BOBBTO11)
        // ชื่อร้านค้าจริงที่เป็นอังกฤษล้วนมักจะมีเว้นวรรค (e.g. "7-Eleven", "Grab Food")
        // ถ้าเป็นคำยาวๆ ที่มีทั้งตัวเลขและตัวอักษรปนกันโดยไม่มีเว้นวรรค ให้ถือว่าเป็นรหัส
        val hasDigits = candidate.any { it.isDigit() }
        val hasLetters = candidate.any { it.isLetter() }
        val hasSpace = candidate.contains(" ")
        val isThai = candidate.any { it in '\u0E00'..'\u0E7F' }

        if (!hasSpace && !isThai && hasDigits && hasLetters) {
            Log.d(TAG, "Filtering out alphanumeric code: $candidate")
            return false
        }

        // ดักชื่ออังกฤษสั้นๆ ที่เป็น uppercase ล้วนโดยไม่มีคำนำหน้า (มักหลุดมาจาก ref code)
        if (!isThai && !hasSpace && candidate.length <= 5 
            && candidate.all { it.isUpperCase() || it.isDigit() }
            && personOrgPrefixes.none { candidate.startsWith(it, ignoreCase = true) }) {
            Log.d(TAG, "Filtering out short uppercase code: $candidate")
            return false
        }

        // กันรหัสที่ยาวเกินไปแม้จะมีตัวอักษรล้วน (เช่น รหัสธุรกรรมที่อ่านผิด)
        if (!hasSpace && !isThai && candidate.length > 15) return false

        if (junkNameKeywords.any { candidate.contains(it) }) return false

        if (line.contains("รหัสอ้างอิง") || 
            line.contains("เลขที่รายการ") || 
            line.contains("จำนวน:") || 
            line.contains("ผู้รับเงินสามารถ") ||
            line.contains("ฟูรับเงินสามารถ") ||
            line.contains("ผูรับเงิน") ||
            line.contains("ข้อมูลเพิ่มเติม")) return false

        if (labelPrefixes.any { line.startsWith(it, ignoreCase = true) }) return false
        if (dateOnlyRegex.containsMatchIn(line) || timeOnlyRegex.containsMatchIn(line)) return false

        // ลบ junk ข้ามบรรทัดและสัญลักษณ์ขยะออก (รวมถึงจุด .) ก่อนตรวจสอบ Keyword
        val cleanLine = line.replace(Regex("""^[\<\>\-\=\:\s\.\,]+"""), "").trim()
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

        val stripped = stripThaiMarks(line)
        
        // หมวดที่ต้องเหมือนเป๊ะทั้งบรรทัด (หรือเกือบเป๊ะ) ถึงจะข้าม
        val keywordsToSkipExact = listOf(
            "จาก", "ไปยัง", "ไปยง", "จํานวนเงิน", "จำนวนเงิน", "จำนวน:", "ผู้รับเงินสามารถ", 
            "ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี้เพื่อตรวจสอบสถานะการจ่ายเงิน",
            "รหัสพร้อมเพย์", "พร้อมเพย์", "PromptPay", "สแกนตรวจสอบสลิป", "สแกนตรวจสอบ",
            "เติมเงินพร้อมเพย์", "เต็มเงินพร้อมเพย์"
        )
        if (keywordsToSkipExact.any { line.equals(it, ignoreCase = true) || stripped.equals(stripThaiMarks(it), ignoreCase = true) }) return true

        // หมวดที่ "มีคำนี้อยู่ในบรรทัด" ก็เพียงพอที่จะข้าม (มักเป็นหัว/ท้ายสลิป)
        val keywordsToSkipContains = listOf(
            "จ่ายบิลสำเร็จ", "ชำระเงินสำเร็จ", "โอนเงินสำเร็จ", "โอนสำเร็จ", "เติมเงินสำเร็จ", 
            "รับเงินสำเร็จ", "รายการสำเร็จ", "สําเร็จ", "สำเร็จ",
            "ข้อมูลเพิ่มเติม", "จากผู้ให้บริการ", "ผู้ให้บริการ", "ตรวจสอบสถานะ", "คิวอาร์โค้ด",
            "ผู้รับเงินสามารถ", "ฟูรับเงินสามารถ", "ผูรับเงิน"
        )
        if (keywordsToSkipContains.any { line.contains(it) || stripped.contains(stripThaiMarks(it)) }) return true

        val skipPrefixes = listOf(
            "รหัสอ้างอิง", "เลขที่รายการ", "เลขอ้างอิง", "เลขที่อ้างอิง", "รหัสธุรกรรม",
            "ค่าธรรมเนียม", "Ref", "Trans",
            "รหสอางอง", "เลขทรายการ", "เลขอางอง", "เลขทอางอง", // Stripped variants
            "รหัสพร้อมเพย์", "พร้อมเพย์", "สแกนตรวจสอบ", "ข้อมูลเพิ่มเติม", "จากผู้ให้บริการ"
        )
        if (skipPrefixes.any { line.startsWith(it, ignoreCase = true) || stripped.startsWith(it, ignoreCase = true) }) return true

        return false
    }

    private data class PartyInfo(
        val senderName: String?,
        val senderAccount: String?,
        val receiverName: String?,
        val receiverAccount: String?
    )
}
