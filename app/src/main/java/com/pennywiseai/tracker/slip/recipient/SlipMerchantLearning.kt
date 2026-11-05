package com.pennywiseai.tracker.slip.recipient

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.database.dao.ScanCorrectionDao
import com.pennywiseai.tracker.data.database.entity.ScanCorrectionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.slip.parser.SlipParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Stores learned recipient names and the old value of every transaction changed by a rule. */
@Singleton
class SlipMerchantLearning @Inject constructor(
    private val database: PennyWiseDatabase,
    private val corrections: ScanCorrectionDao,
    private val transactions: TransactionRepository
) {
    val rules: Flow<List<ScanCorrectionEntity>> = corrections.observeMerchantCorrections()

    suspend fun learn(transactionId: Long, rawReceiver: String, correctedName: String, bankName: String) {
        database.withTransaction {
            corrections.getMerchantCorrectionsList()
                .filter { it.bankName == bankName &&
                    SlipRecipientResolver.normalize(it.originalValue) == SlipRecipientResolver.normalize(rawReceiver) }
                .forEach { forget(it.id) }
            val rule = ScanCorrectionEntity(
                transactionId = transactionId,
                fieldName = "merchantName",
                originalValue = rawReceiver,
                correctedValue = correctedName,
                bankName = bankName
            )
            val ruleId = corrections.insertCorrection(rule)
            for (txn in transactions.getAllTransactionsList()) {
                if (txn.id == transactionId || txn.isDeleted || txn.smsSender != "SLIP_SCAN" ||
                    txn.transactionType == TransactionType.TRANSFER || txn.smsBody.isNullOrBlank() ||
                    txn.merchantName.equals(correctedName, ignoreCase = true)) continue
                val parsed = SlipParser.parse(txn.smsBody)
                if (!matchesBackfillRecipient(
                    rawReceiver, parsed.receiverName, parsed.senderName,
                    bankName, txn.bankName
                )) continue
                corrections.insertCorrection(
                    ScanCorrectionEntity(
                        transactionId = txn.id,
                        fieldName = provenanceField(ruleId),
                        originalValue = txn.merchantName,
                        correctedValue = correctedName,
                        bankName = txn.bankName ?: "unknown"
                    )
                )
                transactions.updateTransaction(
                    txn.copy(
                        merchantName = correctedName,
                        updatedAt = LocalDateTime.now()
                    )
                )
            }
        }
    }

    suspend fun recordAutomaticUse(ruleId: Long, transactionId: Long, originalName: String, correctedName: String, bankName: String) {
        corrections.insertCorrection(
            ScanCorrectionEntity(
                transactionId = transactionId,
                fieldName = provenanceField(ruleId),
                originalValue = originalName,
                correctedValue = correctedName,
                bankName = bankName
            )
        )
    }

    suspend fun forget(ruleId: Long) {
        database.withTransaction {
            val rule = corrections.getCorrectionById(ruleId) ?: return@withTransaction
            if (rule.fieldName != "merchantName") return@withTransaction
            val field = provenanceField(ruleId)
            for (change in corrections.getCorrectionsByField(field)) {
                val txn = transactions.getTransactionById(change.transactionId)
                if (txn != null && !txn.isDeleted && shouldRestoreLearnedName(txn.merchantName, change.correctedValue)) {
                    transactions.updateTransaction(txn.copy(merchantName = change.originalValue))
                }
                // A newer rule may have replaced this name. Make its future
                // rollback skip the deleted rule and return to the raw name.
                corrections.getMerchantProvenance()
                    .filter { it.transactionId == change.transactionId && it.id > change.id && it.originalValue == change.correctedValue }
                    .forEach { newer -> corrections.insertCorrection(newer.copy(originalValue = change.originalValue)) }
            }
            corrections.deleteCorrectionsByField(field)
            corrections.deleteCorrection(ruleId)
        }
    }

    suspend fun forgetAll() {
        for (rule in corrections.observeMerchantCorrections().first().sortedByDescending { it.id }) {
            forget(rule.id)
        }
    }

    private fun provenanceField(ruleId: Long) = "merchantBackfill:$ruleId"
}

internal fun matchesBackfillRecipient(
    learnedReceiver: String,
    scannedReceiver: String?,
    scannedSender: String?,
    learnedBank: String,
    scannedBank: String?
): Boolean =
    !SlipRecipientResolver.looksLikeSameParty(scannedSender, scannedReceiver) &&
        SlipRecipientResolver.matchesLearnedReceiver(learnedReceiver, scannedReceiver) &&
        (learnedBank == "unknown" || learnedBank == scannedBank ||
            SlipRecipientResolver.normalize(learnedReceiver) == SlipRecipientResolver.normalize(scannedReceiver))

internal fun shouldRestoreLearnedName(currentName: String, appliedName: String): Boolean =
    currentName == appliedName
