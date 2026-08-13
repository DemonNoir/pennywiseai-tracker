package com.pennywiseai.tracker.slip.ocr

import com.pennywiseai.tracker.slip.parser.SlipDirection
import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class SlipOcrModelVerificationTest {

    @Test
    fun testAssetFilesExistAndAreValid() {
        val rootDir = File(".").absoluteFile
        val assetsDir = File(rootDir, "src/main/assets/models")

        val detModel = File(assetsDir, "det/inference.onnx")
        val recModel = File(assetsDir, "rec/inference.onnx")
        val keysFile = File(assetsDir, "rec/keys.txt")

        assertTrue("Detection model should exist", detModel.exists())
        assertTrue("Detection model should be non-empty (>1MB)", detModel.length() > 1_000_000)

        assertTrue("Recognition model should exist", recModel.exists())
        assertTrue("Recognition model should be non-empty (~7.9MB)", recModel.length() > 5_000_000)

        assertTrue("Keys file should exist", keysFile.exists())
        val keysLines = keysFile.readLines().map { it.trim('\r', '\n') }
        // 525 = 524 dict chars + 1 trailing SPACE (PaddleOCR use_space_char):
        // the rec model outputs 526 classes = 1 blank + 525 vocab.
        assertEquals("Official th_PP-OCRv5 dictionary should have 525 lines (incl. space)", 525, keysLines.size)

        // Verify key characters
        assertTrue("Keys must include Thai ก", keysLines.contains("ก"))
        assertTrue("Keys must include Thai ฮ", keysLines.contains("ฮ"))
        assertTrue("Keys must include Arabic numeral 0", keysLines.contains("0"))
        assertTrue("Keys must include Arabic numeral 9", keysLines.contains("9"))
        assertTrue("Keys must include English A", keysLines.contains("A"))
        assertTrue("Keys must include English z", keysLines.contains("z"))
        assertTrue("Keys must include Thai Baht symbol ฿", keysLines.contains("฿"))
        assertTrue("Last dict line must be the space char (use_space_char)", keysLines.last() == " ")
    }

    @Test
    fun testOcrTextParsingIntegration() {
        val simulatedOcrText = """
            โอนเงินสำเร็จ
            12 ส.ค. 69 13:45 น.
            นาย สมชาย ใจดี
            ธ.กสิกรไทย
            XXX-X-X1234-X
            นาย ทดสอบ อ
            ธ.กสิกรไทย
            XXX-X-X1111-X
            เลขที่รายการ:
            014223155829103
            จำนวน:
            500.00 บาท
        """.trimIndent()

        val parsed = SlipParser.parse(simulatedOcrText)

        assertNotNull(parsed)
        assertEquals("KBank", parsed.bankName)
        assertEquals(SlipDirection.OUTGOING, parsed.direction)
        assertEquals(BigDecimal("500.00"), parsed.amountBigDecimal)
        assertEquals("014223155829103", parsed.refNo)
    }
}
