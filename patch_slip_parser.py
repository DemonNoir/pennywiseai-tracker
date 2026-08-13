import sys

with open("app/src/main/java/com/pennywiseai/tracker/slip/parser/SlipParser.kt", "r") as f:
    content = f.read()

target = """                    if (senderName == null) {
                        senderName = line
                    } else if (receiverName == null && line != senderName) {
                        // If current senderName doesn't have a prefix but THIS line does, 
                        // maybe the previous one was junk. Swap them.
                        val cleanSender = senderName?.replace(Regex(\"\"\"^[\\<\\>\\-\\=\\:\\s]+\"\"\"), "") ?: ""
                        val senderHasPrefix = listOf("นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", "Mr", "Mrs", "Ms", "Miss", "บจก", "บริษัท").any { cleanSender.startsWith(it, ignoreCase = true) }
                        
                        if (!senderHasPrefix && hasNamePrefix) {
                            senderName = line
                        } else {
                            receiverName = line
                        }
                    } else if (receiverName != null && line != senderName && line != receiverName) {
                        // If current receiverName doesn't have a prefix but THIS line does, replace it.
                        // This handles cases where receiverName was prematurely assigned to OCR junk like "alll"
                        val cleanReceiver = receiverName?.replace(Regex(\"\"\"^[\\<\\>\\-\\=\\:\\s]+\"\"\"), "") ?: ""
                        val receiverHasPrefix = listOf("นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", "Mr", "Mrs", "Ms", "Miss", "บจก", "บริษัท").any { cleanReceiver.startsWith(it, ignoreCase = true) }
                        
                        if (!receiverHasPrefix && hasNamePrefix) {
                            receiverName = line
                        }
                    }"""

replacement = """                    val cleanSender = senderName?.replace(Regex(\"\"\"^[\\<\\>\\-\\=\\:\\s]+\"\"\"), "") ?: ""
                    val senderHasPrefix = listOf("นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", "Mr", "Mrs", "Ms", "Miss", "บจก", "บริษัท").any { cleanSender.startsWith(it, ignoreCase = true) }
                    
                    val cleanReceiver = receiverName?.replace(Regex(\"\"\"^[\\<\\>\\-\\=\\:\\s]+\"\"\"), "") ?: ""
                    val receiverHasPrefix = listOf("นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", "Mr", "Mrs", "Ms", "Miss", "บจก", "บริษัท").any { cleanReceiver.startsWith(it, ignoreCase = true) }

                    if (senderName == null) {
                        senderName = line
                    } else if (receiverName == null && line != senderName) {
                        if (!senderHasPrefix && hasNamePrefix) {
                            senderName = line
                        } else {
                            receiverName = line
                        }
                    } else if (receiverName != null && line != senderName && line != receiverName) {
                        if (!senderHasPrefix && hasNamePrefix) {
                            senderName = line
                        } else if (!receiverHasPrefix && hasNamePrefix) {
                            receiverName = line
                        }
                    }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/pennywiseai/tracker/slip/parser/SlipParser.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")
