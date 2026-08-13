import sys

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val uriStr = _pendingReceiptUri.value ?: _receiptUri.value ?: return\n        val uri = Uri.parse(uriStr)",
    "val uri = _pendingReceiptUri.value ?: _receiptUri.value ?: return"
)

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailViewModel.kt", "w") as f:
    f.write(content)
