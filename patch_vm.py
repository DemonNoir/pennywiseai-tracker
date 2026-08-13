import sys

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
    "import com.pennywiseai.tracker.utils.SmsReportUrlBuilder",
    "import com.pennywiseai.tracker.utils.SmsReportUrlBuilder\nimport com.pennywiseai.tracker.slip.ocr.SlipOcrEngine"
)

content = content.replace(
    "private val receiptManager: ReceiptManager,\n    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context",
    "private val receiptManager: ReceiptManager,\n    private val ocrEngine: SlipOcrEngine,\n    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context"
)

content = content.replace(
    "private val _receiptRemoved = MutableStateFlow(false)\n    val receiptRemoved: StateFlow<Boolean> = _receiptRemoved.asStateFlow()",
    "private val _receiptRemoved = MutableStateFlow(false)\n    val receiptRemoved: StateFlow<Boolean> = _receiptRemoved.asStateFlow()\n    \n    private val _isScanning = MutableStateFlow(false)\n    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()"
)

new_func = """
    fun rescanReceipt() {
        val uriStr = _pendingReceiptUri.value ?: _receiptUri.value ?: return
        val uri = Uri.parse(uriStr)
        
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val rawText = ocrEngine.getRawTextSync(uri)
                val parsed = com.pennywiseai.tracker.slip.parser.SlipParser.parse(rawText)
                
                _editableTransaction.update { current ->
                    if (current == null) return@update null
                    var updated = current.copy(
                        amount = parsed.amountBigDecimal ?: current.amount,
                        merchantName = parsed.receiverName ?: current.merchantName,
                        reference = parsed.refNo ?: current.reference,
                        bankName = parsed.bankName ?: current.bankName,
                        smsBody = rawText
                    )
                    
                    if (parsed.dateTimeIso != null) {
                        try {
                            val parsedDate = LocalDateTime.parse(parsed.dateTimeIso)
                            updated = updated.copy(dateTime = parsedDate)
                        } catch (e: Exception) {}
                    }
                    
                    updated
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to scan receipt: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

"""

content = content.replace(
    "        _showSplitEditor.value = _hasSplits.value\n    }\n\n    fun toggleApplyToAllFromMerchant() {",
    "        _showSplitEditor.value = _hasSplits.value\n    }\n" + new_func + "    fun toggleApplyToAllFromMerchant() {"
)

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailViewModel.kt", "w") as f:
    f.write(content)

