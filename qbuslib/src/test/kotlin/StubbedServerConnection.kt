import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.datatype.DataParseException
import org.muizenhol.qbus.datatype.DataType
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.net.Socket

class StubbedServerConnection(val onData: (ByteArray) -> Unit) {
    val sc: ServerConnection
    private var data = byteArrayOf()

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

    init {
        val outputStream = mock<OutputStream> {
            on { write(any<ByteArray>()) } doAnswer {
                LOG.info("GOT: {}", it)
            }
            on { write(any<ByteArray>(), any<Int>(), any<Int>()) } doAnswer {
                val data2 = it.getArgument<ByteArray>(0)
                val from = it.getArgument<Int>(1)
                val to = it.getArgument<Int>(2)
                data = data.plus(data2.copyOfRange(from, to))

                Unit
            }
            on { flush() } doAnswer {
                onData.invoke(data)
                data = byteArrayOf()

                Unit
            }
        }

        val socket = mock<Socket> {
            on { getOutputStream() } doReturn outputStream
        }
        sc = ServerConnection(socket, MyListener())
    }
}
