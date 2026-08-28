package com.pennywiseai.tracker.slip.recipient

import com.pennywiseai.tracker.data.database.entity.ScanCorrectionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.text.Normalizer
import kotlin.math.max

data class SlipRecipientCandidate(
    val merchantName: String,
    val score: Double,
    val source: SlipRecipientSource
) {
    val canAutoApply: Boolean get() = score >= SlipRecipientResolver.AUTO_APPLY_SCORE
    val canSuggest: Boolean get() = score >= SlipRecipientResolver.SUGGEST_SCORE
}

enum class SlipRecipientSource {
    CORRECTION,
    HISTORY
}

object SlipRecipientResolver {
    const val AUTO_APPLY_SCORE = 0.92
    const val SUGGEST_SCORE = 0.72

    private val BANK_BLACKLIST = setOf(
        "kbank", "scb", "ttb", "gsb", "kkp", "bay", "bbl", "ktb", "uob", "cimb",
        "ธนาคาร", "กสิกร", "ไทยพาณิชย์", "กรุงไทย", "กรุงเทพ", "กรุงศรี", "ออมสิน",
        "k plus", "kplus", "krungthai next", "ttb touch", "my mo", "mymo",
        "bualuang mbank", "bangkok bank", "baac", "ghb", "lhb"
    )

    fun resolve(
        ocrName: String?,
        rawText: String?,
        bankName: String?,
        corrections: List<ScanCorrectionEntity>,
        transactionHistory: List<TransactionEntity>,
        excludedNames: List<String?> = emptyList(),
        limit: Int = 3
    ): List<SlipRecipientCandidate> {
        val normalizedOcr = normalize(ocrName)
        val normalizedRaw = normalize(rawText)
        if (normalizedOcr.length < 3 && normalizedRaw.length < 6) return emptyList()
        val detectedSender = if (!rawText.isNullOrBlank()) {
            try {
                val parsed = com.pennywiseai.tracker.slip.parser.SlipParser.parse(rawText)
                if (!parsed.senderName.isNullOrBlank() && !parsed.receiverName.isNullOrBlank()) {
                    parsed.senderName
                } else null
            } catch (_: Exception) { null }
        } else null

        val allExcluded = (excludedNames + listOfNotNull(detectedSender))
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .map { normalize(it) }
            .filter { it.isNotBlank() && !isSamePartyName(it, normalizedOcr) }

        // Domain guard: slip OCR often reads the sender clearly and the recipient
        // poorly. Never let the sender/self name become a merchant candidate,
        // even when that name appears in correction history or the full raw OCR.
        val correctionCandidates = corrections
            .asSequence()
            .filter { it.fieldName == "merchantName" }
            .filter { bankName == null || it.bankName.equals(bankName, ignoreCase = true) || it.bankName == "unknown" }
            .filterNot { correction -> allExcluded.any { isSamePartyName(correction.correctedValue, it) || isSamePartyName(correction.originalValue, it) } }
            .mapNotNull { correctionCandidate(it, normalizedOcr, normalizedRaw) }

        val historyCandidates = transactionHistory
            .asSequence()
            .filter { !it.isDeleted && it.merchantName.isNotBlank() }
            // Transfers/self-transfers are account movement, not shops. Including
            // them here pollutes merchant matching with the user's own name.
            .filter { it.transactionType != TransactionType.TRANSFER }
            .filter { it.smsSender != "SLIP_SCAN" || it.description?.contains(TransactionEntity.REVIEW_TAG) != true }
            .filterNot { transaction -> allExcluded.any { isSamePartyName(transaction.merchantName, it) } }
            .mapNotNull { historyCandidate(it, normalizedOcr, normalizedRaw, bankName) }

        return (correctionCandidates + historyCandidates)
            .groupBy { normalize(it.merchantName) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.score } }
            .onEach { candidate -> 
                android.util.Log.d("SlipResolver", "Candidate: ${candidate.merchantName}, Score: ${candidate.score}, Source: ${candidate.source}")
            }
            .filterNot { candidate -> allExcluded.any { isSamePartyName(candidate.merchantName, it) } }
            .filter { it.canSuggest && !sameDisplayName(it.merchantName, ocrName) }
            .sortedWith(compareByDescending<SlipRecipientCandidate> { it.score }.thenBy { it.merchantName })
            .take(limit)
    }

    fun bestAutoApply(
        ocrName: String?,
        rawText: String?,
        bankName: String?,
        corrections: List<ScanCorrectionEntity>,
        transactionHistory: List<TransactionEntity>,
        excludedNames: List<String?> = emptyList()
    ): SlipRecipientCandidate? =
        resolve(ocrName, rawText, bankName, corrections, transactionHistory, excludedNames, limit = 1)
            .firstOrNull { it.canAutoApply }

    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[\u0E31\u0E33-\u0E3A\u0E47-\u0E4E]"), "")
            .replace(Regex("[^\\p{IsThai}A-Za-z0-9 ]"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun looksLikeSameParty(senderName: String?, receiverName: String?): Boolean {
        val sender = normalize(senderName)
        val receiver = normalize(receiverName)
        return isSamePartyName(sender, receiver)
    }

    private fun isSamePartyName(leftName: String?, rightName: String?): Boolean {
        val sender = normalize(leftName)
        val receiver = normalize(rightName)
        if (sender.isBlank() || receiver.isBlank()) return false
        if (sender.length < 4 || receiver.length < 4) return sender == receiver
        if (sender == receiver || sender.contains(receiver) || receiver.contains(sender)) return true
        return tokenOverlap(sender, receiver) >= 0.7 || similarity(sender, receiver) >= 0.86
    }

    private fun correctionCandidate(
        correction: ScanCorrectionEntity,
        normalizedOcr: String,
        normalizedRaw: String
    ): SlipRecipientCandidate? {
        val correctedName = correction.correctedValue.trim()
        val normalizedCorrected = normalize(correctedName)

        // 🛡️ Block bank names from corrections too
        if (BANK_BLACKLIST.any { blacklisted ->
                normalizedCorrected == blacklisted || 
                normalizedCorrected.startsWith("$blacklisted ") ||
                normalizedCorrected.endsWith(" $blacklisted")
            }) {
            return null
        }

        val original = normalize(correction.originalValue)
        val originalScore = bestTextScore(original, normalizedOcr, normalizedRaw)
        val correctedScore = bestTextScore(normalizedCorrected, normalizedOcr, normalizedRaw) * 0.96
        val score = max(originalScore, correctedScore)
        if (score < SUGGEST_SCORE) return null
        return SlipRecipientCandidate(
            merchantName = correctedName,
            score = (score + 0.08).coerceAtMost(1.0),
            source = SlipRecipientSource.CORRECTION
        )
    }

    private fun historyCandidate(
        transaction: TransactionEntity,
        normalizedOcr: String,
        normalizedRaw: String,
        bankName: String?
    ): SlipRecipientCandidate? {
        val merchant = transaction.merchantName.trim()
        val normalizedMerchant = normalize(merchant)

        // Hard filter: Do not suggest names that are essentially bank names
        if (BANK_BLACKLIST.any { blacklisted ->
                normalizedMerchant == blacklisted || 
                normalizedMerchant.startsWith("$blacklisted ") ||
                normalizedMerchant.endsWith(" $blacklisted")
            }) {
            return null
        }

        val baseScore = bestTextScore(normalizedMerchant, normalizedOcr, normalizedRaw)
        if (baseScore < SUGGEST_SCORE) return null
        
        // Tie-breaker weights
        val bankBonus = if (bankName != null && transaction.bankName.equals(bankName, ignoreCase = true)) 0.02 else 0.0
        val referenceBonus = if (!transaction.reference.isNullOrBlank()) 0.01 else 0.0
        
        return SlipRecipientCandidate(
            merchantName = merchant,
            score = (baseScore + bankBonus + referenceBonus).coerceIn(0.0, 0.94),
            source = SlipRecipientSource.HISTORY
        )
    }

    private fun sameDisplayName(left: String?, right: String?): Boolean =
        left?.trim()?.equals(right?.trim(), ignoreCase = true) == true

    private fun bestTextScore(candidate: String, normalizedOcr: String, normalizedRaw: String): Double {
        if (candidate.length < 3) return 0.0

        val rawPhraseScore = phraseScore(candidate, normalizedRaw)
        if (rawPhraseScore >= AUTO_APPLY_SCORE) return rawPhraseScore

        val receiverScore = similarity(normalizedOcr, candidate)
        val rawTokenScore = tokenContainmentScore(candidate, normalizedRaw)
        return max(max(receiverScore, rawTokenScore), rawPhraseScore)
    }

    private fun phraseScore(candidate: String, normalizedRaw: String): Double {
        if (normalizedRaw.isBlank()) return 0.0
        
        // Exact word boundary match is best (e.g. "JPT" as a standalone word)
        val wordBoundaryRegex = Regex("(^|[^a-z0-9])${Regex.escape(candidate)}([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
        if (wordBoundaryRegex.containsMatchIn(normalizedRaw)) return 0.98

        // Substring match is okay for long names, but dangerous for short ones like "JPT"
        // which might be part of "JPT2026081232131" (a reference code)
        // If it's a single word (no spaces) and short, we ONLY allow exact boundary match.
        if (candidate.length >= 8 && candidate.contains(" ") && normalizedRaw.contains(candidate)) return 0.92

        return 0.0
    }

    private fun tokenContainmentScore(candidate: String, normalizedRaw: String): Double {
        if (normalizedRaw.isBlank()) return 0.0
        val candidateTokens = significantTokens(candidate)
        if (candidateTokens.isEmpty()) return 0.0

        // Only count hits that match as a whole word/token in the raw text.
        // This prevents "JPT" (token) from matching "JPT2026..." (raw text)
        val hits = candidateTokens.count { token ->
            val pattern = Regex("(^|[^a-z0-9])${Regex.escape(token)}([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
            pattern.containsMatchIn(normalizedRaw)
        }
        
        val ratio = hits.toDouble() / candidateTokens.size
        val lengthBonus = if (candidate.length >= 12) 0.08 else 0.0
        return (ratio * 0.86 + lengthBonus).coerceAtMost(0.93)
    }

    private fun significantTokens(value: String): List<String> {
        val stopWords = setOf(
            "บริษัท", "จำกัด", "จากด", "ร้าน", "shop", "store", "bank", "biller", "id",
            "payment", "transfer", "พร้อมเพย์", "ธนาคาร", "นาย", "นาง", "นางสาว"
        )
        return value.split(" ")
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        if (left.length >= 4 && right.length >= 4 && (left.contains(right) || right.contains(left))) {
            val shorter = minOf(left.length, right.length).toDouble()
            val longer = maxOf(left.length, right.length).toDouble()
            return 0.82 + (shorter / longer * 0.16)
        }

        val tokenScore = tokenOverlap(left, right)
        val editScore = 1.0 - (levenshtein(left, right).toDouble() / max(left.length, right.length).coerceAtLeast(1))
        return max(tokenScore, editScore)
    }

    private fun tokenOverlap(left: String, right: String): Double {
        val leftTokens = left.split(" ").filter { it.length >= 2 }.toSet()
        val rightTokens = right.split(" ").filter { it.length >= 2 }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val overlap = leftTokens.intersect(rightTokens).size
        return overlap.toDouble() / minOf(leftTokens.size, rightTokens.size)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[right.length]
    }
}
