package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.parser.SlipConfidence
import com.pennywiseai.tracker.slip.parser.SlipDirection
import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

class SlipParserTest {

    @Test
    fun testRealKBankSlipParsing() {
        val rawOcrText = """
            จ่ายบิลสําเร็จ                   K
            11 ส.ค. 69 15:37 น.                       +

            นาย ทดสอบ สมมติ
            ธ.กสิกรไทย              ‘          >

            XXX-X-X1111-x

            บริษัท ทดสอบ จำกัด
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

    // --- regression: silent 100x amount bugs from OCR noise (12 ส.ค. 2026) ----

    /**
     * PaddleOCR fires the space class in place of the decimal point: "13 00 บาท"
     * must parse as 13.00, NOT 1300.00. (Real slip slip_test_img; the old space-
     * removal made it 1300 = a silent 100x error.)
     */
    @Test
    fun testAmountWithLostDecimalPoint_IsNotMultipliedBy100() {
        val rawOcrText = """
            จ่ายบิลสำเร็จ
            12 ส.ค. 69 08:22 น.
            นาย ทดสอบ
            ธ.กสิกรไทย
            XXX-X-X1111-x
            บริษัท ทดสอบ จำกัด
            JPT20260812TEST002
            เลขที่รายการ:
            016224082246TEST0002
            จำนวน:
            13 00 บาท
            ค่าธรรมเนียม:
            0.00 บาท
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        assertEquals(BigDecimal("13.00"), parsed.amountBigDecimal)
    }

    /**
     * The fee line (ค่าธรรมเนียม … 0.00 บาท) must never win as the slip amount,
     * even when the real amount OCRs badly. 0.00 here would otherwise be the
     * only well-formed decimal and get chosen as the answer.
     */
    @Test
    fun testFeeLineIsNotPickedAsAmount() {
        val rawOcrText = """
            โอนเงินสำเร็จ
            11 ส.ค. 69 20:51 น.
            นาย สมมติ
            ธ.กสิกรไทย
            เลขที่รายการ:
            016223205135TEST0003
            จำนวน:
            ##########
            ค่าธรรมเนียม:
            0.00 บาท
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        // ไม่มียอดจริงที่อ่านได้ → ต้องเป็น null ไม่ใช่ 0.00 (ห้ามบันทึก 0.00 เงียบๆ)
        assertEquals(null, parsed.amountBigDecimal)
    }

    /**
     * Regression guard for the space-after-dot form "299. 00" (real slip slip_test_img):
     * the space inside the number must be normalised away, keeping 299.00.
     */
    @Test
    fun testAmountWithSpaceAfterDecimalPoint() {
        val rawOcrText = """
            SCB.
            จ่ายบิลสำเร็จ
            08 ส.ค. 2569 - 17:49
            รหัสอ้างอิง: 20260808TEST0000000000001
            จาก นาย ทดสอบ
            XXX-XXX111-3
            ไปยัง ร้านถุงเงิน (ต่อพลาสติก)
            Biller ID : 010753700000000
            จำนวนเงิน
            299. 00
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        assertEquals(BigDecimal("299.00"), parsed.amountBigDecimal)
    }

    /**
     * หลักพันแบบไม่มี comma/จุด ("1 300 บาท") ต้องไม่กลายเป็น 1.30 — การเติมจุด
     * ต้องใช้เฉพาะกับคู่ที่ตามด้วย บาท/THB และไม่มี digit ต่อท้ายเท่านั้น
     */
    @Test
    fun testThousandsAmountWithSpace_IsNotBrokenByDecimalInjection() {
        val rawOcrText = """
            โอนเงินสำเร็จ
            01 ม.ค. 69 09:00 น.
            จาก นาย ก
            ไปยัง นาย ข
            จำนวนเงิน
            1 300 บาท
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        // 1300 (scale 0) == 1300.00 ทางตัวเลข — ใช้ compareTo เหมือน SlipBatchOcrReportTest
        // เพราะ parser คืน scale ตามที่ OCR อ่าน ไม่ได้ pad ทศนิยม
        assertEquals(0, parsed.amountBigDecimal?.compareTo(BigDecimal("1300.00")))
    }

    @Test
    fun testDecimalInjection_KeepsThousandsAndMissingDecimalSeparate() {
        // "1 300" (หลักพันมีช่องว่าง) ต้องเป็น 1300 ไม่ใช่ 13.00
        val thousands = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            01 ม.ค. 69 09:00 น.
            จาก นาย ก
            ไปยัง นาย ข
            จำนวนเงิน
            1 300 บาท
            """.trimIndent()
        )
        assertEquals(0, thousands.amountBigDecimal?.compareTo(BigDecimal("1300.00")))

        // "13 00 บาท" (จุดทศนิยมหาย) ต้องเป็น 13.00 ตามระบบซ่อมจุดทศนิยม (สลิปไทยลงท้าย .00 เสมอ)
        val missingDecimal = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            01 ม.ค. 69 09:00 น.
            จาก นาย ก
            ไปยัง นาย ข
            จำนวนเงิน
            13 00 บาท
            """.trimIndent()
        )
        assertEquals(0, missingDecimal.amountBigDecimal?.compareTo(BigDecimal("13.00")))
    }

    @Test
    fun testRealSCBUploadedSlipParsing() {
        val rawOcrText = """
            ๕๐19:
            @ ง่ายบิลสําเร็จ
            08 ส.ค. 2569 - 17:49
            รหัสอ้างอิง: 20260808TEST0000000000001
            จาก                            @ บงายทดสอบ a.
            XXX-XXX111-3
            ไปยัง ร้านถุงเงิน (ต่อพลาสติก)
            Biller ID : 010753700000000
            รหัสร้านค้า : 1433013000000000000
            รหัสธุรกรรมถุงเงิน : BUNRUAM
            เลขที่อ้างอิง 3 : 0000
            จํานวนเงิน                                 299.00
            ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี้เพื่อ yaaa
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        
        assertEquals(SlipDirection.BILL_PAYMENT, parsed.direction)
        assertEquals("20260808TEST0000000000001", parsed.refNo)
        assertEquals(BigDecimal("299.00"), parsed.amountBigDecimal)
        assertEquals("010753700000000", parsed.billerId)
    }

    @Test
    fun testNoisyThaiOcr_DateAmountRefAndDirection() {
        val rawOcrText = """
            SCB.
            ะศตู-ทMจายบลสาเรืจยรหะ
            E08 ส.ค2569 -1749Eพ
            รหสอางอง: 20260808TEST0000000000002
            จากดนายทดสอบอ.
            4--XXX-XXx111-3
            ไปยัง ร้านกงเงิน(ต่อพลาสติก)
            ---B1er1D : 010753700000000
            จาบวนเงิน 299. 00
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        assertEquals(SlipDirection.BILL_PAYMENT, parsed.direction)
        assertEquals("SCB", parsed.bankName)
        assertEquals(BigDecimal("299.00"), parsed.amountBigDecimal)
        assertEquals("20260808TEST0000000000002", parsed.refNo)
        assertEquals("010753700000000", parsed.billerId)
        assertNotNull(parsed.dateTimeIso)
        assertEquals("ร้านกงเงิน(ต่อพลาสติก)", parsed.receiverName)
    }

    @Test
    fun testAmountLabelWithoutVowels() {
        val rawOcrText = """
            โอนเงนสาเร็จ
            09 ส. ค 2569 -12:44
            จาก นาย ก
            ไปยัง นาย ข
            จานวนเงิน------- 640.93
            รหสอางอง: 20260809329TESTXXX
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals(BigDecimal("640.93"), parsed.amountBigDecimal)
        assertEquals("20260809329TESTXXX", parsed.refNo)
        assertNotNull(parsed.dateTimeIso)
    }

    @Test
    fun testBuddhistLeapDayIsAcceptedByJavaTime() {
        val parsed = SlipParser.parseToLocalDateTime("29 ก.พ. 2567", "10:00 น.")
        assertEquals("2024-02-29T10:00:00", parsed?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Test
    fun testNonLeapFeb29IsRejectedByJavaTime() {
        val parsed = SlipParser.parseToLocalDateTime("29 ก.พ. 2566", "10:00")
        assertEquals(null, parsed)
    }

    @Test
    fun testTwoDigitBeYearUsesThaiBuddhistChronology() {
        val parsed = SlipParser.parseToLocalDateTime("11 ส.ค. 69", "15:37 น.")
        assertEquals("2026-08-11T15:37:00", parsed?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Test
    fun testTwoDigitBeYearBeforeCurrentYearIsNotShiftedForward() {
        // Regression: reduced year ที่ base = ปีปัจจุบัน (2569) ทำให้ "68" -> 2668 (= ค.ศ. 2125)
        // สลิปปีที่แล้ว (พ.ศ. 2568 = "68") ต้องได้ ค.ศ. 2025 ไม่ใช่ 2125
        val parsed = SlipParser.parseToLocalDateTime("11 ส.ค. 68", "15:37 น.")
        assertEquals("2025-08-11T15:37:00", parsed?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Test
    fun testTwoDigitBeYearEarlyEraStaysIn25xx() {
        // "00" = พ.ศ. 2500 (ค.ศ. 1957) ต้องไม่กลายเป็น พ.ศ. 2600
        val parsed = SlipParser.parseToLocalDateTime("11 ส.ค. 00", "15:37 น.")
        assertEquals("1957-08-11T15:37:00", parsed?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Test
    fun testThaiSlipPreviousYearParsesFullFlow() {
        // End-to-end: สลิปไทยปีที่แล้ว (พ.ศ. 2568 = "68") ผ่าน parse() เต็มรูปแบบต้องได้ ค.ศ. 2025
        val rawOcrText = """
            จ่ายบิลสําเร็จ                   K
            11 ส.ค. 68 15:37 น.                       +

            นาย ทดสอบ สมมติ
            ธ.กสิกรไทย              ‘          >

            XXX-X-X1111-x

            บริษัท ทดสอบ จำกัด
            TESTBTOO2

            จำนวนเงิน: 1,250.00 บาท

            เลขที่รายการ:
            016223153750TEST0001
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        assertEquals("2025-08-11T15:37:00", parsed.dateTimeIso)
        // วันที่เป็นอดีต (ไม่ใช่อนาคต 2125) → ไม่โดนบังคับ NEEDS_REVIEW
        assertNotEquals(SlipConfidence.NEEDS_REVIEW, parsed.confidence)
    }

    @Test
    fun testEnglishSlipTwoDigitYearIsAD() {
        // สลิปภาษาอังกฤษ "15 Aug 26" = 15 ส.ค. 2026 (ค.ศ.) — ต้องไม่ถูกตีความเป็น พ.ศ. 2626 (= ค.ศ. 2083)
        val parsed = SlipParser.parseToLocalDateTime("15 Aug 26", "5:56")
        assertEquals("2026-08-15T05:56:00", parsed?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Test
    fun testEnglishSlipFourDigitYearIsAD() {
        val parsed = SlipParser.parseToLocalDateTime("15 Aug 2026", "5:56")
        assertEquals("2026-08-15T05:56:00", parsed?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    @Test
    fun testInvalidClockTimeIsRejectedByJavaTime() {
        assertEquals(null, SlipParser.parseToLocalDateTime("11 ส.ค. 69", "24:01"))
        assertEquals(null, SlipParser.parseToLocalDateTime("11 ส.ค. 69", "12:60"))
    }

    @Test
    fun testFutureDateForcesNeedsReview() {
        // ปี พ.ศ. ปีหน้า (dynamic เพื่อไม่ให้ test พังเมื่อเวลาผ่านไป)
        val futureThaiYear = java.time.LocalDate.now().plusYears(1).year + 543
        val rawOcrText = """
            จ่ายบิลสําเร็จ                   K
            11 ส.ค. $futureThaiYear 15:37 น.                       +

            นาย ทดสอบ สมมติ
            ธ.กสิกรไทย              ‘          >

            XXX-X-X1111-x

            บริษัท ทดสอบ จำกัด
            TESTBTOO2

            จำนวนเงิน: 1,250.00 บาท

            เลขที่รายการ:
            016223153750TEST0001
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        // ข้อมูลครบทุกอย่าง (bank/amount/date/refNo/parties) แต่ OCR อ่านวันที่เพี้ยนเป็นอนาคต
        // → ต้องโดนบังคับ NEEDS_REVIEW ไม่ใช่ CONFIRMED
        assertEquals(SlipConfidence.NEEDS_REVIEW, parsed.confidence)
        assertNotNull(parsed.timestampMillis)
    }

    @Test
    fun testRecentPastDateStillConfirms() {
        // วันที่ผ่านมาไม่นาน (3 วันก่อน) ต้องไม่ถูกบังคับ NEEDS_REVIEW —
        // สร้างจาก now แบบ dynamic เพื่อให้ test ผ่านทุกเดือน/ปี
        val thaiMonths = listOf(
            "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
            "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
        )
        val past = java.time.LocalDate.now().minusDays(3)
        val pastDateStr = "${past.dayOfMonth} ${thaiMonths[past.monthValue - 1]} ${past.year + 543}"
        val rawOcrText = """
            จ่ายบิลสําเร็จ                   K
            $pastDateStr 15:37 น.                       +

            นาย ทดสอบ สมมติ
            ธ.กสิกรไทย              ‘          >

            XXX-X-X1111-x

            บริษัท ทดสอบ จำกัด
            TESTBTOO2

            จำนวนเงิน: 1,250.00 บาท

            เลขที่รายการ:
            016223153750TEST0001
            สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        assertEquals(SlipConfidence.CONFIRMED, parsed.confidence)
    }

    @Test
    fun testKBankPromptPaySlipWithOcrPrefixes() {
        val rawOcrText = """
            โอนเงินสําเร็จ

            28 ส.ค. 69 17:49 น. จ

            wie ปฐวิกรณ์ a

            ธ.กสิกรไทย

            XXX-X-X9937-x

            น.ส.สมบูรณ์ พรมเมธ

            รหัสพร้อมเพย์

            XXX-XXX-0196

            เลขที่รายการ:

            016240174952BP

            จํานวน:

            50.00 บาท

            ค่าธรรมเนียม:

            0.00 บาท สแกนตรวจสอบสลิป
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        assertEquals("KBank", parsed.bankName)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals("ปฐวิกรณ์", parsed.senderName)
        assertEquals("XXX-X-X9937-x", parsed.senderAccount)
        assertEquals("น.ส.สมบูรณ์ พรมเมธ", parsed.receiverName)
        assertEquals("XXX-XXX-0196", parsed.receiverAccount)
        assertEquals(BigDecimal("50.00"), parsed.amountBigDecimal)
        assertEquals("016240174952BP", parsed.refNo)
        assertEquals(SlipConfidence.CONFIRMED, parsed.confidence)
    }

    @Test
    fun testOutgoingWithoutReceiverIsNotConfirmed() {
        val rawOcrText = """
            โอนเงินสำเร็จ
            28 ส.ค. 69 17:49 น.
            ธ.กสิกรไทย
            เลขที่รายการ: 016240174952BP
            จำนวนเงิน: 50.00 บาท
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)
        // Outgoing transfer without receiver should not be CONFIRMED
        assertNotEquals(SlipConfidence.CONFIRMED, parsed.confidence)
    }

    @Test
    fun testScbPromptPayTopUpWithAdditionalInfo() {
        val rawOcrText = """
            SCB
            i                 *
            08 ส.ค. 2569 - 11:55
            รหัสอ้างอิง: 202608082JLb3RYN2WRAnPByF
            จาก                       @ uns ปฐวีกรณ์ อบฟัง
            XXX-XXX994-3
            ไปยัง เต็มเงินพร้อมเพย์
            004999109017292
            จํานวนเงิน                               200.00
            ข้อมูลเพิ่มเติมจากผู้ให้บริการ
            wa. นิจจารีย์  (K Plus W)
            ฟูรับเงินสามารถสแกนคิวอาร์โค้ดน่เพือ
            ตรวจสอบสถานะการเติมเงิน
        """.trimIndent()

        val parsed = SlipParser.parse(rawOcrText)

        assertEquals("SCB", parsed.bankName)
        assertEquals("ปฐวีกรณ์ อบฟัง", parsed.senderName)
        assertEquals("XXX-XXX994-3", parsed.senderAccount)
        assertEquals("นิจจารีย์ (K Plus W)", parsed.receiverName)
        assertEquals("004999109017292", parsed.receiverAccount)
        assertEquals(BigDecimal("200.00"), parsed.amountBigDecimal)
        assertEquals("202608082JLb3RYN2WRAnPByF", parsed.refNo)
    }
}


