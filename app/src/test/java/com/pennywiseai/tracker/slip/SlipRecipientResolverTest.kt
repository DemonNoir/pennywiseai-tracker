package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.data.database.entity.ScanCorrectionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.slip.recipient.SlipRecipientResolver
import com.pennywiseai.tracker.slip.recipient.SlipRecipientSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class SlipRecipientResolverTest {

    @Test
    fun `exact correction can be auto applied`() {
        val candidates = SlipRecipientResolver.resolve(
            ocrName = "ร้านถุงเงน",
            rawText = "โอนเงินสำเร็จ\nร้านถุงเงน\nจำนวนเงิน 100.00 บาท",
            bankName = "SCB",
            corrections = listOf(
                correction(original = "ร้านถุงเงน", corrected = "ร้านถุงเงิน"),
            ),
            transactionHistory = emptyList()
        )

        assertEquals("ร้านถุงเงิน", candidates.first().merchantName)
        assertEquals(SlipRecipientSource.CORRECTION, candidates.first().source)
        assertTrue(candidates.first().canAutoApply)
    }

    @Test
    fun `history fuzzy match is suggested but not overconfident`() {
        val candidates = SlipRecipientResolver.resolve(
            ocrName = "TrueMoney Shop ไอเดียผกสด",
            rawText = "SCB\nจ่ายบิลสำเร็จ\nTrueMoney Shop (ไอเดียผักสด)\nBiller ID : 010554614000000",
            bankName = "SCB",
            corrections = emptyList(),
            transactionHistory = listOf(
                transaction("TrueMoney Shop (ไอเดียผักสด)", bankName = "SCB")
            )
        )

        assertEquals("TrueMoney Shop (ไอเดียผักสด)", candidates.first().merchantName)
        assertTrue(candidates.first().canSuggest)
    }

    @Test
    fun `weak unrelated names are not suggested`() {
        val best = SlipRecipientResolver.bestAutoApply(
            ocrName = "ร้านกาแฟบ้านสวน",
            rawText = "โอนเงินสำเร็จ\nร้านกาแฟบ้านสวน\nจำนวนเงิน 50.00 บาท",
            bankName = "KBank",
            corrections = emptyList(),
            transactionHistory = listOf(transaction("ค่าไฟฟ้านครหลวง", bankName = "KBank"))
        )

        assertNull(best)
    }

    @Test
    fun `raw OCR text can recover full merchant when parsed receiver is incomplete`() {
        val candidates = SlipRecipientResolver.resolve(
            ocrName = "TrueMoney Shop",
            rawText = """
                SCB
                จ่ายบิลสำเร็จ
                ไปยัง
                TrueMoney Shop (ไอเดียผักสด)
                Biller ID : 010554614000000
                จำนวนเงิน
                120.00
            """.trimIndent(),
            bankName = "SCB",
            corrections = emptyList(),
            transactionHistory = listOf(
                transaction("TrueMoney Shop (ไอเดียผักสด)", bankName = "SCB")
            )
        )

        assertEquals("TrueMoney Shop (ไอเดียผักสด)", candidates.first().merchantName)
        assertTrue(candidates.first().canAutoApply)
    }

    @Test
    fun `same sender and receiver is detected as same party`() {
        assertTrue(
            SlipRecipientResolver.looksLikeSameParty(
                senderName = "นาย สมชาย ใจดี",
                receiverName = "สมชาย ใจดี"
            )
        )
    }

    @Test
    fun `transfer transactions are not used as merchant history`() {
        val candidates = SlipRecipientResolver.resolve(
            ocrName = "นาย สมชาย ใจดี",
            rawText = "โอนเงินสำเร็จ\nจาก\nนาย สมชาย ใจดี\nไปยัง\nนาย สมชาย ใจดี",
            bankName = "KBank",
            corrections = emptyList(),
            transactionHistory = listOf(
                transaction(
                    merchantName = "นาย สมชาย ใจดี",
                    bankName = "KBank",
                    transactionType = TransactionType.TRANSFER
                )
            )
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `sender name is excluded while raw OCR can still match recipient`() {
        val candidates = SlipRecipientResolver.resolve(
            ocrName = "นาย สมชาย ใจดี",
            rawText = """
                โอนเงินสำเร็จ
                จาก
                นาย สมชาย ใจดี
                ไปยัง
                ร้านถุงเงิน (ต่อพลาสติก)
                จำนวนเงิน 299.00
            """.trimIndent(),
            bankName = "SCB",
            corrections = listOf(
                correction(original = "นาย สมชาย ใจดี", corrected = "นาย สมชาย ใจดี"),
            ),
            transactionHistory = listOf(
                transaction("ร้านถุงเงิน (ต่อพลาสติก)", bankName = "SCB")
            ),
            excludedNames = listOf("นาย สมชาย ใจดี")
        )

        assertEquals("ร้านถุงเงิน (ต่อพลาสติก)", candidates.first().merchantName)
    }

    private fun correction(
        original: String,
        corrected: String,
        bankName: String = "SCB"
    ) = ScanCorrectionEntity(
        transactionId = 1L,
        fieldName = "merchantName",
        originalValue = original,
        correctedValue = corrected,
        bankName = bankName
    )

    private fun transaction(
        merchantName: String,
        bankName: String = "SCB",
        transactionType: TransactionType = TransactionType.EXPENSE
    ) = TransactionEntity(
        amount = BigDecimal("100.00"),
        merchantName = merchantName,
        category = "Others",
        transactionType = transactionType,
        dateTime = LocalDateTime.of(2026, 8, 17, 12, 0),
        bankName = bankName,
        smsSender = "SLIP_SCAN",
        transactionHash = merchantName
    )
}
