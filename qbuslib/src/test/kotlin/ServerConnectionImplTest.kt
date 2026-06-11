import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.ServerConnectionImpl
import org.muizenhol.qbus.datatype.DataParseException
import org.muizenhol.qbus.datatype.DataType
import org.muizenhol.qbus.datatype.Version
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

@ExperimentalUnsignedTypes
class ServerConnectionImplTest : StringSpec() {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    private val dataStruct = DataStruct()
    private lateinit var serverSocket: ServerSocket

    override suspend fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        serverSocket = ServerSocket(0)
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        serverSocket.close()
    }

    init {
        "readWelcome succeeds when server sends a welcome message" {
            val clientSocket = Socket("localhost", serverSocket.localPort)
            val serverSide = serverSocket.accept()
            try {
                thread(isDaemon = true) {
                    serverSide.getOutputStream().write("Welcome to QBUS TCP/IP v6.06".toByteArray())
                    serverSide.getOutputStream().flush()
                }
                val sc = ServerConnectionImpl(clientSocket, noopListener())
                sc.readWelcome()
            } finally {
                serverSide.close()
                clientSocket.close()
            }
        }

        "startDataReader fires onEvent when server sends a valid framed message" {
            val clientSocket = Socket("localhost", serverSocket.localPort)
            val serverSide = serverSocket.accept()
            val received = CompletableDeferred<DataType>()
            val listener = object : ServerConnection.Listener {
                override fun onEvent(event: DataType) { received.complete(event) }
                override fun onParseException(ex: DataParseException) { received.completeExceptionally(RuntimeException(ex)) }
                override fun onConnectionClosed() {}
            }
            try {
                val sc = ServerConnectionImpl(clientSocket, listener)
                sc.startDataReader()
                thread(isDaemon = true) {
                    serverSide.getOutputStream().write(dataStruct.version)
                    serverSide.getOutputStream().flush()
                }
                val event = withTimeout(5_000) { received.await() }
                assertThat(event, instanceOf(Version::class.java))
            } finally {
                serverSide.close()
                clientSocket.close()
            }
        }

        "close stops the reader without triggering onConnectionClosed" {
            val clientSocket = Socket("localhost", serverSocket.localPort)
            val serverSide = serverSocket.accept()
            val closedDeferred = CompletableDeferred<Unit>()
            val listener = object : ServerConnection.Listener {
                override fun onEvent(event: DataType) {}
                override fun onParseException(ex: DataParseException) {}
                override fun onConnectionClosed() { closedDeferred.complete(Unit) }
            }
            try {
                val sc = ServerConnectionImpl(clientSocket, listener)
                sc.startDataReader()
                sc.close()
                // Allow time for any spurious callback to fire
                val fired = withTimeoutOrNull(400) { closedDeferred.await() }
                assert(fired == null) { "onConnectionClosed should not fire on deliberate close" }
            } finally {
                serverSide.close()
            }
        }

        "startDataReader fires onConnectionClosed when server resets the connection" {
            val clientSocket = Socket("localhost", serverSocket.localPort)
            val serverSide = serverSocket.accept()
            val closedDeferred = CompletableDeferred<Unit>()
            val listener = object : ServerConnection.Listener {
                override fun onEvent(event: DataType) {}
                override fun onParseException(ex: DataParseException) {}
                override fun onConnectionClosed() { closedDeferred.complete(Unit) }
            }
            try {
                val sc = ServerConnectionImpl(clientSocket, listener)
                sc.startDataReader()
                // setSoLinger(true, 0) forces RST on close, which triggers IOException on the client
                serverSide.setSoLinger(true, 0)
                serverSide.close()
                withTimeout(5_000) { closedDeferred.await() }
            } finally {
                clientSocket.close()
            }
        }
    }

    private fun noopListener() = object : ServerConnection.Listener {
        override fun onEvent(event: DataType) {}
        override fun onParseException(ex: DataParseException) {}
        override fun onConnectionClosed() {}
    }
}
