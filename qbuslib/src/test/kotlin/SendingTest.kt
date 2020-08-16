import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.muizenhol.qbus.Common
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

@ExperimentalUnsignedTypes
class SendingTest : StringSpec() {
    lateinit var sc: ServerConnection
    private val data = mutableListOf<ByteArray>()
    lateinit var ctrl: Controller

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    }

    override fun beforeTest(testCase: TestCase) {
        data.clear()
        val stub = StubbedServerConnection { dataItem -> data.add(dataItem) }
        sc = stub.sc
        ctrl = Controller("serial", "user", "pass", "localhost",
            { exception -> LOG.error("Exception", exception) },
            connectionCreator = { _, _, _ -> stub.sc })
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        ctrl.close()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 6)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 6] = '0'
            hexChars[j * 6 + 1] = 'x'
            hexChars[j * 6 + 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 6 + 3] = HEX_ARRAY[v and 0x0F]
            hexChars[j * 6 + 4] = 'u'
            hexChars[j * 6 + 5] = ','
        }
        return String(hexChars)
    }

    private fun writeEvent(value: Byte) {
        val place = SdDataStruct.Place(14, "myplace")
        val out = SdDataStruct.Output(12, "myname", 0x07, 0x02, 13, place, false, SdDataStruct.Type.ON_OFF)
        out.value = value
        ctrl.setNewState(out)
    }

    init {
        "Sending version" {
            sc.writeMsgVersion()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0xFFu, 0x00u, 0x05u, 0x2Au, 0x07u, 0x00u, 0x00u, 0x04u, 0x23u
            ).toByteArray()
        }
        "Sending controller options" {
            sc.writeControllerOptions()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0xffu, 0x00u, 0x05u, 0x2au, 0x0du, 0x0du, 0x00u, 0x07u, 0x23u
            ).toByteArray()
        }
        "Sending Login" {
            sc.login("myUser", "myPass")
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0xFAu, 0x00u, 0x22u, 0x2Au, 0x00u, 0x6Du, 0x79u, 0x55u, 0x73u,
                0x65u, 0x72u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0x00u, 0x00u, 0x6Du, 0x79u, 0x50u, 0x61u, 0x73u, 0x73u,
                0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0x23u
            ).toByteArray()
        }
        "Sending FAT data" {
            sc.writegetFATData()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x05u, 0x2au, 0x09u, 0x00u, 0x00u,
                0x00u, 0x23u
            ).toByteArray()
        }
        "Sending get SD header" {
            sc.writegetSDData()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x05u, 0x2au, 0x44u, 0x29u, 0xffu,
                0xefu, 0x23u
            ).toByteArray()
        }
        "Sending get SD part1" {
            sc.writegetSDData(1)
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x05u, 0x2au, 0x44u, 0x29u, 0xfeu,
                0xefu, 0x23u
            ).toByteArray()
        }
        "Sending write OFF event via controller" {
            writeEvent(0x00)
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x06u, 0x2au, 0xb8u, 0x07u, 0x02u,
                0x00u, 0x00u, 0x23u
            ).toByteArray()
        }
        "Sending write ON event via controller" {
            writeEvent(Common.XFF)
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x06u, 0x2au, 0xb8u, 0x07u, 0x02u,
                0x00u, 0xffu, 0x23u
            ).toByteArray()
        }
    }
}

