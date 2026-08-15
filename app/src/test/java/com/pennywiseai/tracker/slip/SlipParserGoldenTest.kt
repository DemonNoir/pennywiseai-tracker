package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.parser.SlipConfidence
import com.pennywiseai.tracker.slip.parser.SlipDirection
import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SlipParserGoldenTest {

    @Test
    fun testGoldenSlip_FullyPopulated() {
        val rawOcrText = """
            โอนเงินสำเร็จ
            12 ก.ค. 69 - 18:45 น.
            จาก SCB
            นาย สมชาย ใจดี
            xxx-x-x123-4
            ไปยัง กรุงไทย
            บริษัท ร้านค้าออนไลน์ จำกัด
            xxx-x-x567-8
            จำนวนเงิน
            350.50 บาท
            ค่าธรรมเนียม: 0.00 บาท
            รหัสอ้างอิง: 2026071288991234
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        assertEquals("SCB", parsed.bankName)
        assertEquals("โอนเงินสำเร็จ", parsed.transactionType)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals("12 ก.ค. 69", parsed.date)
        assertEquals("18:45", parsed.time)
        assertEquals("2026071288991234", parsed.refNo)
        assertEquals("นาย สมชาย ใจดี", parsed.senderName)
        assertEquals("xxx-x-x123-4", parsed.senderAccount)
        assertEquals("บริษัท ร้านค้าออนไลน์ จำกัด", parsed.receiverName)
        assertEquals("xxx-x-x567-8", parsed.receiverAccount)
        assertEquals(BigDecimal("350.50"), parsed.amountBigDecimal)
    }

    @Test
    fun testFeeInlineWithAmount() {
        // ทดสอบเคสที่ OCR รวมค่าธรรมเนียมกับยอดจริงไว้ในบรรทัดเดียวกัน
        val rawOcrText = """
            โอนเงินสำเร็จ
            12 ก.ค. 69 - 18:45 น.
            จาก SCB
            นาย สมชาย
            ไปยัง กรุงไทย
            บริษัท ร้านค้า
            ค่าธรรมเนียม 0.00 บาท ยอดรวม 500.00 บาท
            รหัสอ้างอิง: 2026071288991234
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        // ต้องได้ยอด 500.00 ไม่ใช่ null (เพราะถูกลบทั้งบรรทัด) และไม่ใช่ 0.00
        assertEquals(BigDecimal("500.00"), parsed.amountBigDecimal)
    }
}
