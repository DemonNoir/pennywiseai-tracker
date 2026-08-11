package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.parser.SlipConfidence
import com.pennywiseai.tracker.slip.parser.SlipDirection
import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

class SlipParserTest {

    @Test
    fun testRealKBankSlipParsing() {
        val rawOcrText = """
            จ่ายบิลสําเร็จ                   K
            11 ส.ค. 69 15:37 น.                       +

            นาย ทดสอบ a          ae
            ธ.กสิกรไทย              ‘          >

            XXX-X-X1111-x

            บริษัท ทดสอบ จํากัด
            JPT2026081168713
            TESTBTOO2

            จำนวนเงิน: 1,250.00 บาท

            เลขที่รายการ:
            016223153750TEST0001
            ง5 a     |    |
            ts     = eS 0 =
            . ค่าธรรมเนียม: 0.00 บาท

            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        assertEquals("KBank", parsed.bankName)
        assertEquals(SlipDirection.BILL_PAYMENT, parsed.direction)
        assertNotNull("RefNo should be detected", parsed.refNo)
        assertEquals("016223153750TEST0001", parsed.refNo)
        assertEquals(BigDecimal("1250.00"), parsed.amountBigDecimal)
    }

    @Test
    fun testRealSCBSlipParsing() {
        val rawOcrText = """
            โอนเงินสำเร็จ
            12 ก.ค. 69 - 18:45 น.
            
            จาก SCB
            นาย สมชาย ใจดี
            x-1234
            
            ไปยัง กรุงไทย
            บริษัท ร้านค้าออนไลน์ จำกัด
            x-5678
            
            จำนวนเงิน
            350.50 บาท
            
            รหัสอ้างอิง: 2026071288991234
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        assertEquals("SCB", parsed.bankName)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals(BigDecimal("350.50"), parsed.amountBigDecimal)
        assertEquals("2026071288991234", parsed.refNo)
    }

    @Test
    fun testFallbackAmountExtractionWithoutLabel() {
        val garbledOcrText = """
            โอนสำเร็จ
            10 มิ.ย. 69 09:12
            จาก กสิกรไทย
            ไปยัง กรุงศรี
            
            ข้อความรบกวน OCR
            2,890.00
            ค่าธรรมเนียม 0.00
            Ref: 99881122
        """.trimIndent()

        val parsed = SlipParser.parse(garbledOcrText)

        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals(BigDecimal("2890.00"), parsed.amountBigDecimal)
        assertEquals("99881122", parsed.refNo)
    }

    @Test
    fun testIncomingDirectionPriority() {
        val incomingOcrText = """
            รับเงินสำเร็จ
            05 พ.ค. 69 14:20 น.
            โอนเงินเข้าบัญชีของคุณ
            ยอดเงิน 500.00 บาท
            จาก นาย อเนก
            รหัสอ้างอิง: INC123456
        """.trimIndent()

        val parsed = SlipParser.parse(incomingOcrText)

        assertEquals(SlipDirection.INCOMING, parsed.direction)
        assertEquals(BigDecimal("500.00"), parsed.amountBigDecimal)
    }
}

