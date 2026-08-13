import sys

with open("app/src/main/java/com/pennywiseai/tracker/slip/parser/SlipParser.kt", "r") as f:
    content = f.read()

target = """        val namePrefixes = listOf(
            "นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", 
            "Mr.", "Mrs.", "Ms.", "Miss", "MR.", "MRS.", "MS.", 
            "บจก.", "บริษัท", "ร้าน", "TrueMoney", "AIS", "บัตรเครดิต", "Shop", "Store", "Co.,Ltd"
        )
        if (namePrefixes.any { line.startsWith(it, ignoreCase = true) }) return true

        val uppercaseCompanyPattern = Regex(\"\"\"^[A-Z0-9\\s\\.\\&\\-]{3,}$\"\"\")
        if (uppercaseCompanyPattern.matches(line) && line.length >= 3) return true

        val namePattern = Regex(\"\"\"^[\\u0E00-\\u0E7FA-Za-z0-9\\s\\(\\)\\.\\/\\&\\–-]{3,}$\"\"\")
        return namePattern.matches(line)"""

replacement = """        val namePrefixes = listOf(
            "นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", 
            "Mr.", "Mrs.", "Ms.", "Miss", "MR.", "MRS.", "MS.", 
            "บจก.", "บริษัท", "ร้าน", "TrueMoney", "AIS", "บัตรเครดิต", "Shop", "Store", "Co.,Ltd"
        )
        val cleanLine = line.replace(Regex(\"\"\"^[\\<\\>\\-\\=\\:\\s]+\"\"\"), "")
        if (namePrefixes.any { cleanLine.startsWith(it, ignoreCase = true) }) return true

        val uppercaseCompanyPattern = Regex(\"\"\"^[A-Z0-9\\s\\.\\&\\-]{3,}$\"\"\")
        if (uppercaseCompanyPattern.matches(cleanLine) && cleanLine.length >= 3) return true

        val namePattern = Regex(\"\"\"^[\\u0E00-\\u0E7FA-Za-z0-9\\s\\(\\)\\.\\/\\&\\–-]{3,}$\"\"\")
        return namePattern.matches(cleanLine)"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/pennywiseai/tracker/slip/parser/SlipParser.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")
