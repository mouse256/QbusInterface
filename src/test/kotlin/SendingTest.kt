import com.nhaarman.mockitokotlin2.*
import io.kotlintest.TestCase
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.datatype.DataParseException
import org.muizenhol.qbus.datatype.DataType
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.net.Socket

@ExperimentalUnsignedTypes
class SendingTest : StringSpec() {
    lateinit var sc: ServerConnection
    lateinit var outputStream: OutputStream
    private val data = mutableListOf<ByteArray>()

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    }

    class MyListener : ServerConnection.Listener {
        override fun onEvent(event: DataType) {
            //ILB
        }

        override fun onParseException(ex: DataParseException) {
            throw RuntimeException("Test failed", ex)
        }
    }

    override fun beforeTest(testCase: TestCase) {
        data.clear()
        outputStream = mock<OutputStream> {
            on { write(any<ByteArray>()) } doAnswer {
                LOG.info("GOT: {}", it)
            }
            on { write(any<ByteArray>(), any<Int>(), any<Int>()) } doAnswer {
                var data2 = it.getArgument<ByteArray>(0)
                var from = it.getArgument<Int>(1)
                var to = it.getArgument<Int>(2)
                data.add(data2.copyOfRange(from, to))

                Unit
            }
        }
        // data = data + data

        val socket = mock<Socket> {
            on { getOutputStream() } doReturn outputStream
        }
        sc = ServerConnection(socket, MyListener())
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

    init {
        "Sending version" {
            sc.writeMsgVersion()
            verify(outputStream).flush()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0xFFu, 0x00u, 0x05u, 0x2Au, 0x07u, 0x00u, 0x00u, 0x04u, 0x23u
            ).toByteArray()
        }
        "Sending controller options" {
            sc.writeControllerOptions()
            verify(outputStream).flush()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0xffu, 0x00u, 0x05u, 0x2au, 0x0du, 0x0du, 0x00u, 0x07u, 0x23u
            ).toByteArray()
        }
        "Sending Login" {
            sc.login("myUser", "myPass")
            verify(outputStream).flush()
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
            verify(outputStream).flush()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x05u, 0x2au, 0x09u, 0x00u, 0x00u,
                0x00u, 0x23u
            ).toByteArray()
        }
        "Sending get SD header" {
            sc.writegetSDData()
            verify(outputStream).flush()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x05u, 0x2au, 0x44u, 0x29u, 0xffu,
                0xefu, 0x23u
            ).toByteArray()
        }
        "Sending get SD part1" {
            sc.writegetSDData(1)
            verify(outputStream).flush()
            data.size shouldBe 1
            data[0] shouldBe ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x05u, 0x2au, 0x44u, 0x29u, 0xfeu,
                0xefu, 0x23u
            ).toByteArray()
        }
    }
}

