@file:Suppress("BlockingMethodInNonBlockingContext")

import com.nhaarman.mockitokotlin2.mock
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.datatype.DataParseException
import org.muizenhol.qbus.datatype.DataType
import org.muizenhol.qbus.datatype.SDData
import org.muizenhol.qbus.sddata.SdDataParser
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.invoke.MethodHandles
import java.net.Socket

@ExperimentalUnsignedTypes
class SdDataTest : StringSpec() {
    lateinit var sc: ServerConnection
    private val dataStruct = DataStruct()
    private val myListener = MyListener()

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    private class MyListener : ServerConnection.Listener {
        var listData: ((SDData.SDDataBlock) -> Unit)? = null
        var listHeader: ((SDData.SDDataHeader) -> Unit)? = null

        override fun onEvent(event: DataType) {
            LOG.info("Event of {}", event.javaClass)
            if (listData == null || listHeader == null) {
                throw IllegalStateException("Event not expected")
            } else {
                when (event) {
                    is SDData.SDDataBlock -> listData?.invoke(event)
                    is SDData.SDDataHeader -> listHeader?.invoke(event)
                }
            }
        }

        override fun onParseException(ex: DataParseException) {
            throw RuntimeException("Test failure", ex)
        }

        override fun onConnectionClosed() {
            throw RuntimeException("Test failed")
        }

        fun setDataListener(listener: (SDData.SDDataBlock) -> Unit) {
            this.listData = listener
        }

        fun setHeaderListener(listener: (SDData.SDDataHeader) -> Unit) {
            this.listHeader = listener
        }
    }

    override fun beforeTest(testCase: TestCase) {
        val socket = mock<Socket> {}
        sc = ServerConnection(socket, myListener)
    }

    init {
        "Parsing version data" {
            val sdDataParser = SdDataParser()
            var expectHeader = true
            var count = 0
            val file = File("/tmp/qbusout.zip")
            LOG.info("Parsing raw data")
            BufferedOutputStream(FileOutputStream(file)).use { writer ->
                myListener.setHeaderListener {
                    assert(expectHeader)
                    expectHeader = false
                    sc.parse(dataStruct.sdPart1)
                }
                myListener.setDataListener { sdData ->
                    assert(!expectHeader)
                    count++
                    assert(count == sdData.nr)

                    sdDataParser.addData(sdData.getData())
                    /*LOG.info(
                    "SD data begin: {}",
                    sdData.getData().copyOfRange(0, 5).toString(StandardCharsets.UTF_8)
                )*/
                    writer.write(sdData.getData())
                    when (count) {
                        1 -> sc.parse(dataStruct.sdPart2)
                        2 -> sc.parse(dataStruct.sdPart3)
                        3 -> sc.parse(dataStruct.sdPart4)
                        4 -> sc.parse(dataStruct.sdPart5)
                        5 -> sc.parse(dataStruct.sdPart6)
                        6 -> sc.parse(dataStruct.sdPart7)
                        7 -> LOG.info("Parsed last SDData block")
                        else -> throw IllegalStateException("Unexpected id")
                    }
                }
                sc.parse(dataStruct.sdHeader)
            }

            LOG.info("Parsing zip data")
            sdDataParser.parse()

        }
        "Parsing json data" {
            val parser = SdDataParser()
            parser.parse(FileInputStream(File("out.json")))
        }

    }
}
