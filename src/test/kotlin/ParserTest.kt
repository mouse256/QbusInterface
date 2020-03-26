import io.kotlintest.specs.StringSpec
import java.net.Socket
import com.nhaarman.mockitokotlin2.*
import io.kotlintest.TestCase
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.datatype.DataType
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

@ExperimentalUnsignedTypes
class ParserTest : StringSpec() {
    lateinit var sc: ServerConnection
    val dataStruct = DataStruct()

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    class MyListener: ServerConnection.Listener {
        override fun onEvent(event: DataType) {
            //ILB
        }
    }

    override fun beforeTest(testCase: TestCase) {
        val socket = mock<Socket> {}
        sc = ServerConnection(socket, MyListener())
    }

    init {
        "Parsing version data" {
            sc.parse(dataStruct.version)
        }
        "Parsing login data" {
            LOG.info("Test 2")
            val data = byteArrayOf(
                0x51, 0x42, 0x55, 0x53, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xfa.toByte(), 0x00, 0x04, 0x2a, 0x00, 0x00, 0x23,

                0x51, 0x42, 0x55, 0x53, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xfa.toByte(), 0x00, 0x1f, 0x2a, 0x02, 0x57, 0x65,
                0x6c, 0x63, 0x6f, 0x6d, 0x65, 0x20, 0x74, 0x6f,
                0x20, 0x51, 0x42, 0x55, 0x53, 0x20, 0x54, 0x43,
                0x50, 0x2f, 0x49, 0x50, 0x20, 0x76, 0x36, 0x2e,
                0x30, 0x36, 0x23,
                0x51, 0x42, 0x55, 0x53, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xff.toByte(), 0x00, 0x10, 0x2a, 0x0d, 0x0d, 0x00,
                0x07, 0x00, 0x07, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x23
            )
            sc.parse(data)
        }
        "Status update" {
            LOG.info("Status update")
            val data = ubyteArrayOf(
                0x51u, 0x42u, 0x55u, 0x53u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x00u, 0xffu, 0x00u, 0x0du, 0x2au, 0x38u, 0x0cu, 0xffu,
                0x00u, 0x00u, 0x04u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
                0x23u
            ).toByteArray()
            sc.parse(data)
        }
        "Event" {
            LOG.info("Event")
            sc.parse(dataStruct.event)
        }
        "EventDouble" {
            LOG.info("Event")
            sc.parse(dataStruct.event.plus(dataStruct.fatData))
        }
        "EventWith0x23" {
            LOG.info("Event")
            sc.parse(dataStruct.eventWith0x23)
        }
        "VersionWith0x23" {
            LOG.info("Event")
            sc.parse(dataStruct.versionWith0x23)
        }
        "FAT data" {
            LOG.info("FAT data")
            sc.parse(dataStruct.fatData)
        }
        "SD header" {
            LOG.info("SD header")
            sc.parse(dataStruct.sdHeader)
        }
        "SD data" {
            LOG.info("SD data")
            sc.parse(dataStruct.sdPart1)
            sc.parse(dataStruct.sdPart2)
            sc.parse(dataStruct.sdPart3)
            sc.parse(dataStruct.sdPart4)
            sc.parse(dataStruct.sdPart5)
            sc.parse(dataStruct.sdPart6)
            sc.parse(dataStruct.sdPart7)
        }
    }
}
