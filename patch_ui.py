import sys

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt", "r") as f:
    content = f.read()

btn_code = """
        if (displayReceiptUri != null) {
            val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = { viewModel.rescanReceipt() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning...")
                } else {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-scan Receipt")
                }
            }
        }
"""

content = content.replace(
    "        ReceiptPickerSection(\n            receiptUri = displayReceiptUri,\n            onReceiptSelected = { uri -> viewModel.updatePendingReceiptUri(uri) },\n            onReceiptRemoved = { viewModel.removeReceipt() },\n            onCreateCameraUri = { viewModel.createCameraUri() }\n        )",
    "        ReceiptPickerSection(\n            receiptUri = displayReceiptUri,\n            onReceiptSelected = { uri -> viewModel.updatePendingReceiptUri(uri) },\n            onReceiptRemoved = { viewModel.removeReceipt() },\n            onCreateCameraUri = { viewModel.createCameraUri() }\n        )\n" + btn_code
)

if "import androidx.compose.material3.CircularProgressIndicator" not in content:
    content = content.replace(
        "import androidx.compose.material3.Checkbox",
        "import androidx.compose.material3.Checkbox\nimport androidx.compose.material3.CircularProgressIndicator"
    )

if "import androidx.compose.material.icons.filled.DocumentScanner" not in content:
    content = content.replace(
        "import androidx.compose.material.icons.filled.Edit",
        "import androidx.compose.material.icons.filled.Edit\nimport androidx.compose.material.icons.filled.DocumentScanner"
    )

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt", "w") as f:
    f.write(content)

