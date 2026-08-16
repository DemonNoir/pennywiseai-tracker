package com.pennywiseai.tracker.domain.usecase

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.manager.TransactionDeduplication
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.repository.*
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.slip.parser.ParsedSlip
import com.pennywiseai.tracker.slip.parser.SlipConfidence
import com.pennywiseai.tracker.slip.parser.SlipDirection
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UseCase สำหรับประมวลผลสลิปที่ผ่านการ OCR แล้ว
 * โดยใช้ Logic การบันทึกเดียวกับระบบ SMS (Rules, Category Mapping, Deduplication)
 */
@Singleton
class ProcessSlipUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val receiptManager: com.pennywiseai.tracker.data.receipt.ReceiptManager
) {
    suspend fun execute(parsedSlip: ParsedSlip): Long {
        // 1. แปลง ParsedSlip เป็น ParsedTransaction (เพื่อใช้ Logic ร่วมกับระบบหลัก)
        val amount = parsedSlip.amountBigDecimal ?: parsedSlip.amount?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
        
        // ──────────────────────────────────────────────────────────────────────
        // Determine transaction type from slip content:
        //   • BILL_PAYMENT direction → always Expense
        //   • sender name ≈ receiver name (fuzzy, OCR-tolerant) → Transfer
        //     (user is moving money between their own accounts)
        //   • everything else, including INCOMING (OCR misread) → Expense
        //   • INCOME is intentionally never produced by slip scanning
        // ──────────────────────────────────────────────────────────────────────
        val type = when {
            parsedSlip.direction == SlipDirection.BILL_PAYMENT ->
                com.pennywiseai.parser.core.TransactionType.EXPENSE

            senderMatchesReceiver(parsedSlip.senderName, parsedSlip.receiverName) ->
                com.pennywiseai.parser.core.TransactionType.TRANSFER

            else ->
                com.pennywiseai.parser.core.TransactionType.EXPENSE
        }

        val parsedTransaction = ParsedTransaction(
            amount = amount,
            type = type,
            merchant = parsedSlip.receiverName ?: parsedSlip.bankName ?: "Bank Slip",
            reference = parsedSlip.refNo,
            accountLast4 = parsedSlip.senderAccount?.let { acc -> 
                val digits = acc.filter { it.isDigit() }
                if (digits.length >= 4) digits.takeLast(4) else null
            },
            balance = null,
            smsBody = parsedSlip.rawText,
            sender = "SLIP_SCAN",
            timestamp = parsedSlip.timestampMillis ?: System.currentTimeMillis(),
            bankName = parsedSlip.bankName ?: "Thai Bank",
            transactionHash = com.pennywiseai.tracker.slip.parser.TransactionHashGenerator.generate(parsedSlip),
            currency = "THB"
        )

        // 2. แปลงเป็น Entity และใช้ Logic มาตรฐาน (Merchant mapping, Rules)
        var entity = parsedTransaction.toEntity()
        
        // ดึงหมวดหมู่ที่ผู้ใช้เคยแมปไว้ (จากชื่อร้านค้า/ผู้รับ)
        val merchantMappingCache = merchantMappingRepository.getAllMappingsAsMap()
        val customCategory = merchantMappingCache[entity.merchantName]
        if (customCategory != null) {
            entity = entity.copy(category = customCategory)
        }

        // ตรวจสอบกฎ (Rule Engine)
        val activeRules = ruleRepository.getActiveRulesByType(entity.transactionType)
        val (withRules, _) = ruleEngine.evaluateRules(entity, parsedSlip.rawText, activeRules)
        entity = withRules

        // เพิ่มข้อความ [รอตรวจสอบ] หากความแม่นยำต่ำ
        if (parsedSlip.confidence != SlipConfidence.CONFIRMED) {
            val note = if (entity.description.isNullOrBlank()) "[รอตรวจสอบ]" else "${entity.description} [รอตรวจสอบ]"
            entity = entity.copy(description = note)
        }

        // 3. ตรวจสอบรายการซ้ำ (Deduplication)
        val existing = transactionRepository.getTransactionByHash(entity.transactionHash)
        if (existing != null) {
            Log.d("ProcessSlipUseCase", "Skipping duplicate slip: ${entity.transactionHash}")
            return existing.id
        }

        var finalEntity = entity
        if (parsedSlip.imageUriString != null) {
            try {
                val receiptPath = receiptManager.saveReceipt(android.net.Uri.parse(parsedSlip.imageUriString))
                if (receiptPath != null) {
                    finalEntity = finalEntity.copy(receiptPath = receiptPath)
                }
            } catch (e: Exception) {
                Log.e("ProcessSlipUseCase", "Failed to save receipt image: ${e.message}")
            }
        }

        // 4. บันทึกลงฐานข้อมูลและอัปเดตยอดเงินในบัญชี
        val rowId = accountBalanceRepository.insertTransactionWithBalance(
            transaction = finalEntity,
            bankName = finalEntity.bankName,
            accountLast4 = finalEntity.accountNumber
        )

        Log.i("ProcessSlipUseCase", "Saved slip transaction: ID=$rowId, Merchant=${finalEntity.merchantName}, Amount=${finalEntity.amount}")
        return rowId
    }

    /**
     * Returns true when [sender] and [receiver] are likely the same person,
     * indicating an inter-account transfer rather than an outgoing payment.
     *
     * Designed to be tolerant of common OCR errors:
     *   - Missing Thai tone marks / diacritics
     *   - One character added or dropped
     *   - Mixed case (for English names)
     *
     * Algorithm:
     *   1. Normalize both names (strip tone marks, lowercase, collapse spaces)
     *   2. Exact match on short names (< 4 chars) — avoids false positives
     *   3. Containment check — one name is a substring of the other
     *   4. Token-overlap check — share ≥ 70 % of word-tokens
     */
    private fun senderMatchesReceiver(sender: String?, receiver: String?): Boolean {
        if (sender.isNullOrBlank() || receiver.isNullOrBlank()) return false

        val s = normalizeForCompare(sender)
        val r = normalizeForCompare(receiver)

        if (s.isEmpty() || r.isEmpty()) return false

        // Short names must match exactly to avoid false positives (e.g. "สมชาย" vs "สมศรี")
        if (s.length < 4 || r.length < 4) return s == r

        // One name is a sub-string of the other (handles prefix/suffix OCR additions)
        if (s.contains(r) || r.contains(s)) return true

        // Token-level overlap: split on whitespace, keep tokens ≥ 2 chars
        val sTokens = s.split(" ").filter { it.length >= 2 }.toSet()
        val rTokens = r.split(" ").filter { it.length >= 2 }.toSet()
        if (sTokens.isEmpty() || rTokens.isEmpty()) return s == r
        val overlap = sTokens.intersect(rTokens).size
        val minSize = minOf(sTokens.size, rTokens.size)
        return overlap.toFloat() / minSize >= 0.7f
    }

    /**
     * Strips characters that OCR commonly mangles so that two representations
     * of the same name compare equal:
     *   - Thai tone marks and vowel shorteners (\u0E48-\u0E4E range)
     *   - Latin combining diacritics (NFD decomposition)
     *   - Non-alphabetic noise except spaces
     * Result is lowercased and has runs of spaces collapsed to one.
     */
    private fun normalizeForCompare(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            // Remove Latin combining diacritics (e.g. accents on romanised Thai)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            // Remove Thai tone marks: mai ek/tho/tri/jattawa, mai tai khu,
            // thanthahat, nikhahit, yamakkan (\u0E48–\u0E4E)
            .replace(Regex("[\u0E48-\u0E4E]"), "")
            // Keep only Thai script, ASCII letters, digits and spaces
            .replace(Regex("[^\u0E00-\u0E7FA-Za-z0-9 ]"), "")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
}
