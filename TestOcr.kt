import com.pennywiseai.tracker.slip.parser.SlipParser
import com.pennywiseai.tracker.slip.parser.PartyInfo

fun main() {
    val rawText = """
แล                1<
แวว 15:37                                   นว
นาย ทดสอบ อ          SE
ธ.กสิกรไทย         ร      โ       ว
XXX-X-X1111-x       5 i
<        บริษัท บอสเชน จำกัด   พ
        ง151202608116สทิร5แห
TESTBT002              น
เลขที่รายการ:           |            BUH iit
puis                   ไง
016223153750APM000558       Ope         [a] iS
a: SG (Se eee    iF         le
: a a ae Rae 13,00 บาท : a                  a4
:  .  ร จ a 0.00 wn สแกนตรวจสอบสลิป | '
    """.trimIndent()
    val parsed = SlipParser.parse(rawText)
    println("Sender: " + parsed.senderName)
    println("Receiver: " + parsed.receiverName)
}
