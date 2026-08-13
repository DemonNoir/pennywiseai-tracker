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
        
        val type = when (parsedSlip.direction) {
            SlipDirection.BILL_PAYMENT -> com.pennywiseai.parser.core.TransactionType.EXPENSE
            SlipDirection.OUTGOING -> com.pennywiseai.parser.core.TransactionType.EXPENSE
            SlipDirection.INCOMING -> com.pennywiseai.parser.core.TransactionType.INCOME
            else -> com.pennywiseai.parser.core.TransactionType.EXPENSE
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
            transactionHash = parsedSlip.refNo ?: "SLIP_${System.currentTimeMillis()}",
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
}
