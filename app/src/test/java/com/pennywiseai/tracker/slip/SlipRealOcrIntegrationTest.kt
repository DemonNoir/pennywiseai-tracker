package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.parser.SlipDirection
import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Feeds REAL OCR output of the user's uploaded SCB bill-payment slip
 * (`/root/uploaded_slip.jpg`, scanned 08 ส.ค. 2569 17:49, 299.00 THB,
 * Biller ID 010753700088205) through SlipParser.
 *
 * Two engines were used:
 *  1. `simulate_ocr.py` — faithful port of the app's SlipOcrEngine (PaddleOCR
 *     det + rec + CTC). Thai glyph accuracy is poor but digits survive.
 *  2. `tesseract tha+eng` — far better Thai, some noise lines.
 */
class SlipRealOcrIntegrationTest {

    private fun report(label: String, parsed: com.pennywiseai.tracker.slip.parser.ParsedSlip) {
        println("""
            === $label ===
            bankName      = ${parsed.bankName}
            direction     = ${parsed.direction}
            confidence    = ${parsed.confidence}
            amount        = ${parsed.amountBigDecimal}
            date          = ${parsed.date}
            time          = ${parsed.time}
            refNo         = ${parsed.refNo}
            receiverName  = ${parsed.receiverName}
            senderName    = ${parsed.senderName}
        """.trimIndent())
    }

    // --- 1. PaddleOCR (app simulation) output --------------------------------
    @Test
    fun testPaddleOcrOutput_RealSlip() {
        // Raw output of slip_ocr_test/simulate_ocr.py on uploaded_slip.jpg
        // (refreshed after the keys.txt use_space_char fix: the model now emits
        // real spaces, including one inside the amount "299. 00" — the parser
        // must normalise that away).
        val rawOcrText = """
            SCB.
            ะศตู-ทMจายบลสาเรืจยรหะ
            E08 ส.ค2569 -1749Eพ
             รหสอางอง: 202608087.6vZAEXwEYEn9B6Y
            จากดนายปจวกรณอ.
            4--XXX-XXx594-3
            ไปยัง ร้านกงเงิน(ต่อพลาสติก)
            ---B1er1D : 010753700088205
            1ศห- -ศ รหสร้านค้า : 1433013044022303218
            แรหสธรกรรมถงงน:BUNUAME
            -แเลขทอางอง 3 :0000
             จาบวนเงิน 299. 00
            ผรบงนสามารถสแกนควอารเคดนเพอเนรหรบ
            -ตรอจสอบลกาบะการจายงปเรรย---
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        report("PaddleOCR real output", parsed)

        // Digits survive OCR: the amount must always be caught.
        assertEquals(BigDecimal("299.00"), parsed.amountBigDecimal)
        // Bank "SCB." is recognised.
        assertEquals("SCB", parsed.bankName)
        // Thai words are garbled beyond recognition ("จายบลสาเรืจย") so direction
        // detection must not crash — but is not required to be BILL_PAYMENT here.
        assertNotNull(parsed.direction)
    }

    // --- 2. Tesseract (tha+eng) output ----------------------------------------
    @Test
    fun testTesseractOutput_RealSlip() {
        val rawOcrText = """
            ๕๐19:
            @ ง่ายบิลสําเร็จ
            08 ส.ค. 2569 - 17:49
            รหัสอ้างอิง: 202608087L6yzAEXwFYfn9B6Y
            จาก                            @ บงายปชูวีกรณ์ a.
            XXX-XXX994-3
            ไปยัง ร้านถุงเงิน (ต่อพลาสติก)
            Biller ID : 010753700088205
            รหัสร้านค้า : 1433013044022303218
            รหัสธุรกรรมถุงเงิน : BUNRUAM
            เลขที่อ้างอิง 3 : 0000
            จํานวนเงิน                                 299.00
            ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี้เพื่อ yaaa
            1    _                   + aE in  ม hem,
            ตรวจสอบสถานะการจ่ายเงิน
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        report("Tesseract real output", parsed)

        assertEquals(BigDecimal("299.00"), parsed.amountBigDecimal)
        // Tesseract preserves Thai: amount label "จํานวนเงิน", date, ref no, receiver.
        assertEquals("202608087L6yzAEXwFYfn9B6Y", parsed.refNo)
        // "ง่ายบิลสําเร็จ" (จ่าย→ง่าย OCR slip) + "Biller ID" → should be BILL_PAYMENT
        assertEquals(SlipDirection.BILL_PAYMENT, parsed.direction)
        // Receiver line contains a full-width space: "ร้านถุงเงิน (ต่อพลาสติก)"
        assertTrue(
            "receiver should contain ร้านถุงเงิน, was: ${parsed.receiverName}",
            parsed.receiverName?.contains("ร้านถุงเงิน") == true
        )
    }
}
