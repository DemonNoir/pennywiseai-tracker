import sys

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt", "r") as f:
    content = f.read()

target = """            }
        }


        // Bank (read-only)"""

replacement = """            }
        }

        if (!transaction.smsBody.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ExpandableSmsSection(smsBody = transaction.smsBody)
        }

        // Bank (read-only)"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found")
    sys.exit(1)

with open("app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt", "w") as f:
    f.write(content)
