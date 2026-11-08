package com.pennywiseai.tracker.slip

import com.pennywiseai.tracker.slip.recipient.matchesBackfillRecipient
import com.pennywiseai.tracker.slip.recipient.shouldRestoreLearnedName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipMerchantLearningTest {
    @Test
    fun `historical slip uses extracted receiver not another name in raw text`() {
        assertTrue(matchesBackfillRecipient("ร้านถุงเงิน", "ร้านถุงเงน", "ผู้โอน", "SCB", "SCB"))
        assertFalse(matchesBackfillRecipient("ร้านถุงเงิน", "ร้านกาแฟบ้านสวน", "ผู้โอน", "SCB", "SCB"))
        assertFalse(matchesBackfillRecipient("ร้านสมพรขายของชำ", "ร้านสมพรขายขงชำ", "ผู้โอน", "SCB", "KBank"))
        assertTrue(matchesBackfillRecipient("ร้านถุงเงิน", "ร้านถุงเงิน", "ผู้โอน", "SCB", "KBank"))
        assertFalse(matchesBackfillRecipient("ร้านถุงเงิน", "ร้านถุงเงน", "ร้านถุงเงน", "SCB", "SCB"))
    }

    @Test
    fun `reset restores only names still set by the learned rule`() {
        assertTrue(shouldRestoreLearnedName("ร้านถุงเงิน", "ร้านถุงเงิน"))
        assertFalse(shouldRestoreLearnedName("ชื่อที่แก้เองภายหลัง", "ร้านถุงเงิน"))
    }
}
