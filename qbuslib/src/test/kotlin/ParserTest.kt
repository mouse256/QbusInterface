import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import org.mockito.kotlin.mock
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.ServerConnectionImpl
import org.muizenhol.qbus.datatype.DataParseException
import org.muizenhol.qbus.datatype.DataType
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.net.Socket

@ExperimentalUnsignedTypes
class ParserTest : StringSpec() {
    lateinit var sc: ServerConnectionImpl
    val dataStruct = DataStruct()

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    class MyListener : ServerConnection.Listener {
        override fun onEvent(event: DataType) {
            //ILB
        }

        override fun onParseException(ex: DataParseException) {
            throw RuntimeException("Test failed", ex)
        }

        override fun onConnectionClosed() {
            throw RuntimeException("Test failed")
        }
    }

    override suspend fun beforeTest(testCase: TestCase) {
        val socket = mock<Socket> {}
        sc = ServerConnectionImpl(socket, MyListener())
    }

    init {
        "Parsing version data" {
            sc.parse(dataStruct.version)
        }
        "Parsing login data" {
            LOG.info("Login data")
            sc.parse(dataStruct.loginData)
        }
        "Status update" {
            LOG.info("Status update")
            sc.parse(dataStruct.statusUpdate)
        }
        "Status update write" {
            LOG.info("Status update with write bit set")
            sc.parse(dataStruct.statusUpdateWrite)
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
