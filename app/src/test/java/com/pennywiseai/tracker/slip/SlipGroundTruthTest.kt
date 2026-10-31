package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.parser.SlipDirection
import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class SlipGroundTruthTest {

    @Test
    fun testKBankGroundTruth() {
        val rawText = """
            โอนเงินสำเร็จ
            3 ส.ค. 69 21:25 น.
            นาย ทดสอบ อ
            ธ.กสิกรไทย
            XXX-X-X1111-X
            นาย สมมติ ใจดี
            ธ.กสิกรไทย
            XXX-X-X2222-X
            เลขที่รายการ:
            016215212503TEST0004
            จำนวน:
            3,166.00 บาท
            ค่าธรรมเนียม:
            0.00 บาท
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawText)

        assertEquals("KBank", parsed.bankName)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals(BigDecimal("3166.00"), parsed.amountBigDecimal)
        assertEquals("016215212503TEST0004", parsed.refNo)
        assertEquals("นาย สมมติ ใจดี", parsed.receiverName)
        assertEquals(LocalDateTime.of(2026, 8, 3, 21, 25), SlipParser.parseToLocalDateTime(parsed.date, parsed.time))
    }

    @Test
    fun testSCBGroundTruth() {
        val rawText = """
            SCB
            จ่ายบิลสำเร็จ
            09 ส.ค. 2569 - 12:57
            รหัสอ้างอิง: 202608095TEST000000000000
            จาก
            นาย ทดสอบ อ.
            XXX-XXX111-3
            ไปยัง
            TrueMoney Shop (ไอเดียผักสด)
            Biller ID : 010554614000000
            รหัสร้านค้า : M0000000000000000000
            รหัสสาขา : S000000000000000000
            จำนวนเงิน
            120.00
            ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี้เพื่อ
            ตรวจสอบสถานะการจ่ายเงิน
        """.trimIndent()

        val parsed = SlipParser.parse(rawText)

        assertEquals("SCB", parsed.bankName)
        assertEquals(SlipDirection.BILL_PAYMENT, parsed.direction)
        assertEquals(BigDecimal("120.00"), parsed.amountBigDecimal)
        assertEquals("202608095TEST000000000000", parsed.refNo)
        assertEquals("TrueMoney Shop (ไอเดียผักสด)", parsed.receiverName)
        assertEquals(LocalDateTime.of(2026, 8, 9, 12, 57), SlipParser.parseToLocalDateTime(parsed.date, parsed.time))
    }

    @Test
    fun testKTBGroundTruth() {
        val rawText = """
            โอนเงินสำเร็จ
            10 ส.ค. 69 14:30 น.
            นาง ทดสอบ ข
            กรุงไทย
            XXX-X-X3333-X
            ไปยัง กรุงเทพ
            บริษัท เอบีซี จำกัด
            XXX-X-X4444-X
            เลขที่รายการ:
            2026081014300001
            จำนวนเงิน
            500.00 บาท
            ค่าธรรมเนียม 0.00 บาท
        """.trimIndent()

        val parsed = SlipParser.parse(rawText)

        assertEquals("Krungthai", parsed.bankName)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals(BigDecimal("500.00"), parsed.amountBigDecimal)
        assertEquals("2026081014300001", parsed.refNo)
        assertEquals("บริษัท เอบีซี จำกัด", parsed.receiverName)
        assertEquals(LocalDateTime.of(2026, 8, 10, 14, 30), SlipParser.parseToLocalDateTime(parsed.date, parsed.time))
    }
}
