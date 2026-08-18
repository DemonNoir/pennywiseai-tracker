package com.pennywiseai.tracker.data.entity

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionReviewTagTest {

    @Test
    fun hasReviewTag_detectsTagInDescription() {
        assertTrue(TransactionEntity.hasReviewTag("ค่าอาหาร [รอตรวจสอบ]"))
        assertTrue(TransactionEntity.hasReviewTag("[รอตรวจสอบ]"))
        assertFalse(TransactionEntity.hasReviewTag("ค่าอาหาร"))
        assertFalse(TransactionEntity.hasReviewTag(null))
    }

    @Test
    fun clearReviewTag_removesTagAndKeepsRest() {
        assertEquals("ค่าอาหาร", TransactionEntity.clearReviewTag("ค่าอาหาร [รอตรวจสอบ]"))
        assertEquals("ค่าอาหาร", TransactionEntity.clearReviewTag("[รอตรวจสอบ] ค่าอาหาร"))
    }

    @Test
    fun clearReviewTag_handlesTagOnlyDescriptionAndNull() {
        // ป้ายล้วน ๆ → เหลือ string ว่าง → กลายเป็น null
        assertNull(TransactionEntity.clearReviewTag("[รอตรวจสอบ]"))
        assertNull(TransactionEntity.clearReviewTag(null))
        // ไม่มีป้าย → คืนค่าเดิม
        assertEquals("ค่าอาหาร", TransactionEntity.clearReviewTag("ค่าอาหาร"))
    }
}
