fun main() {
    val line = "< บริษัท ทดสอบ จํากัด เห"
    val cleanLine = line.replace(Regex("""^[\<\>\-\=\:\s]+"""), "")
    println("cleanLine: '$cleanLine'")
    val hasPrefix = listOf("นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.", "Mr", "Mrs", "Ms", "Miss", "บจก", "บริษัท").any { cleanLine.startsWith(it, ignoreCase = true) }
    println("hasPrefix: $hasPrefix")
    
    val alll = "alll"
    val isAlllValid = alll.length >= 3 // and other checks
    
    // Check splitNameAndAccount
    val inlineAccountNoRegex = Regex("""[XxX\-\*]{3,}[\d\-\*]*\d+|[\d\-\*]+[XxX\-\*]{3,}""")
    val match = inlineAccountNoRegex.find("XXX-X-X1111-X alll")
    println("acc: ${match?.value}")
}
