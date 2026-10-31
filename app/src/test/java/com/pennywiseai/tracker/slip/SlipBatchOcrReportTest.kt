package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.parser.SlipParser
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * Batch report: runs SlipParser over the REAL OCR output of every uploaded slip
 * (saved under the slip_ocr_test ocr_outputs folder, one dir per engine) and
 * prints a per-file, per-engine parse report. Read the report from the test's stdout.
 *
 * Assertions (this is the regression guard for real-world OCR noise):
 *  1. every slip yields a positive amount from at least one engine (minimum for
 *     ProcessSlipUseCase to save anything);
 *  2. the parsed amount MATCHES the real amount printed on the slip (ground
 *     truth, read from the physical slip) on at least one engine — not just
 *     "non-null". Uses compareTo so "1300" == "1300.00".
 */
class SlipBatchOcrReportTest {

    // ยอดเงินจริงที่พิมพ์บนสลิปแต่ละใบ (อ่านจากรูปสลิปจริง + ยืนยันด้วย pixel
    // cluster / tesseract crop เมื่อ 12 ส.ค. 18:xx):
    //   - slip_kbank_bill_1 = 13.00 (ไม่ใช่ 1300! พิสูจน์จาก pixel cluster 1,3,.,0,0)
    //   - slip_kbank_transfer_1 = 31.00 (ไม่ใช่ 310.00)
    //   - slip_unknown_1 = 3166.00 แต่ถูกตัดออกโดยตั้งใจ: OCR ทั้ง 2 engine อ่านตัว
    //     หลักพันหาย (Paddle: เละทั้งใบ, Tesseract: "166.00" ตัว 3 หลุด) —
    //     เป็น known OCR limitation ไม่มีทางกู้คืนโดยไม่ใช้ความรู้จากนอกสลิป
    private val expectedAmounts = mapOf(
        "slip_scb_bill_1" to BigDecimal("640.93"),  // SCB จ่ายบิล
        "slip_scb_bill_2" to BigDecimal("299.00"),  // SCB จ่ายบิล
        "slip_scb_topup_1" to BigDecimal("20.00"),   // SCB เติมเงิน
        "slip_scb_bill_3" to BigDecimal("330.00"),  // SCB จ่ายบิล
        "slip_scb_bill_4" to BigDecimal("120.00"),  // SCB จ่ายบิล
        "slip_kbank_transfer_1" to BigDecimal("31.00"),   // KBank โอน (ตัว 0 ไม่หายแล้ว)
        "slip_kbank_bill_1" to BigDecimal("13.00"),   // KBank จ่ายบิล (Paddle อ่าน "13 00" → parser ต้องได้ 13.00)
    )

    private fun ocrDir(engine: String): File =
        File("/Users/ginkless/Desktop/pennywiseai-tracker-full/slip_ocr_test/ocr_outputs/$engine")

    private fun findTxtFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "txt" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    @Ignore("Local manual test requiring real OCR outputs")
    @Test
    fun reportAllSlips() {
        val engines = listOf("paddle", "tess")
        val rawBySlip = LinkedHashMap<String, MutableMap<String, String>>()

        engines.forEach { engine ->
            findTxtFiles(ocrDir(engine)).forEach { file ->
                rawBySlip.getOrPut(file.name.removeSuffix(".txt")) { mutableMapOf() }[engine] = file.readText()
            }
        }

        println("=================================================================")
        println("BATCH OCR -> SlipParser REPORT (${rawBySlip.size} slips)")
        println("=================================================================")
        rawBySlip.forEach { (slip, enginesOut) ->
            println("\n### $slip")
            engines.forEach { e ->
                val text = enginesOut[e]
                if (text == null) {
                    println("  [$e] (no OCR output)")
                } else {
                    val parsed = SlipParser.parse(text)
                    println(
                        "  [$e] bank=${parsed.bankName ?: "-"} | dir=${parsed.direction} | " +
                            "conf=${parsed.confidence} | amt=${parsed.amountBigDecimal ?: "-"} | " +
                            "date=${parsed.date ?: "-"} | time=${parsed.time ?: "-"} | " +
                            "ref=${parsed.refNo ?: "-"} | recv=${parsed.receiverName ?: "-"}"
                    )
                }
            }
        }

        // Invariant 1: ทุกใบต้องมี amount อย่างน้อย 1 engine
        var slipsWithAmount = 0
        rawBySlip.forEach { (_, enginesOut) ->
            val anyAmount = enginesOut.values.any { t -> SlipParser.parse(t).amountBigDecimal != null }
            if (anyAmount) slipsWithAmount++
        }
        println("\nSlips with amount extracted: $slipsWithAmount / ${rawBySlip.size}")
        assertTrue("At least one slip failed to yield an amount", slipsWithAmount >= rawBySlip.size)

        // Invariant 2: amount ต้องตรงกับยอดจริงบนสลิป (ground truth) อย่างน้อย 1 engine
        var correctAmountSlips = 0
        println("\n=== AMOUNT CORRECTNESS (ground truth) ===")
        expectedAmounts.forEach { (slip, expected) ->
            val enginesOut = rawBySlip[slip] ?: return@forEach
            val parsedAll = engines.mapNotNull { e -> enginesOut[e]?.let { SlipParser.parse(it) } }
            val got = parsedAll.map { it.amountBigDecimal?.toPlainString() ?: "-" }.joinToString(", ")
            val anyCorrect = parsedAll.any { it.amountBigDecimal?.compareTo(expected) == 0 }
            println("### $slip expected=$expected got=[$got] -> ${if (anyCorrect) "OK" else "MISMATCH"}")
            assertTrue("$slip: expected amount $expected but got [$got]", anyCorrect)
            if (anyCorrect) correctAmountSlips++
        }
        println("\nSlips with correct amount: $correctAmountSlips / ${expectedAmounts.size}")
        assertTrue("Not all slips parsed the correct amount", correctAmountSlips >= expectedAmounts.size)
    }
}
