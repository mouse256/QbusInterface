import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.*
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.*
import org.muizenhol.qbus.Common
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.ServerConnection
import org.muizenhol.qbus.datatype.*
import org.muizenhol.qbus.exception.InvalidSerialException
import org.muizenhol.qbus.exception.LoginException
import org.muizenhol.qbus.exception.QbusException
import org.muizenhol.qbus.sddata.SdDataJson
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.lang.invoke.MethodHandles
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


@ExperimentalUnsignedTypes
class ControllerTest : StringSpec() {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    private lateinit var controller: Controller
    private lateinit var serverConnection: ServerConnection
    private lateinit var dataZip: ByteArray
    private var loginOK = true
    private val serialDefault = "010203"
    private var serial = serialDefault
    val outOnOff1 = SdDataJson.Outputs(
        address = 1,
        subAddress = 0,
        controllerId = 0,
        id = 30,
        originalName = "out1",
        shortName = "o1",
        typeId = 0,//TODO
        real = true,
        system = true,
        eventsOnSd = true,
        placeId = 0, //TODO
        iconNr = 1
    )
    private var stateHandler: (Controller.State) -> Unit = {}
    private var exceptionHandler: (QbusException) -> Unit = {}

    override fun beforeTest(testCase: TestCase) {
        stateHandler = { _ -> }
        exceptionHandler = { ex -> LOG.error("Unhandled exception", ex) }
        loginOK = true
        serial = serialDefault
        prepareSDData()
        serverConnection = mock {
            on { writeMsgVersion() } doAnswer {
                LOG.debug("Mocking version")
                sendEvent(Version("01.02", serial))
                void()
            }
            on { login(anyString(), anyString()) } doAnswer {
                LOG.debug("Mocking login")
                sendEvent(PasswordVerify(loginOK))
                void()
            }
            on { writeControllerOptions() } doAnswer {
                LOG.debug("Mocking controller options")
                sendEvent(ControllerOptions(0x00, byteArrayOf()))
                void()
            }
            on { writegetFATData() } doAnswer {
                LOG.debug("Mocking fat data")
                sendEvent(FatData())
                void()
            }
            on { writegetSDData(anyInt()) } doAnswer { invocation ->
                handleSdData(invocation.getArgument(0))
            }
            on { writeGetAddressStatus(anyByte()) } doAnswer { invocation ->
                handleGetStatus(invocation.getArgument(0))
            }
        }
        controller = Controller(serialDefault, "username", "password", "localhost",
            { exception -> exceptionHandler.invoke(exception) },
            { stateUpdate -> stateHandler.invoke(stateUpdate) },
            connectionCreator = {_, _, _ -> serverConnection},
            reconnectTimeout = Duration.ZERO,
            sleepOnStart = Duration.ZERO
        )
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        controller.close()
    }

    private fun void() {}

    private fun sendEvent(event: DataType): Job {
        //add a very short delay and handle from a different thread
        return GlobalScope.launch {
            withContext(Dispatchers.IO) {
                Thread.sleep(10)
                controller.onEvent(event)
            }
        }
    }

    private fun handleSdData(part: Int) {
        LOG.debug("Mocking sd data ({})", part)
        when (part) {
            0 -> sendEvent(SDData.SDDataHeader(80, 50))
            1 -> sendEvent(SDData.SDDataBlock(0xFE, dataZip.copyOfRange(0, 10)))
            2 -> sendEvent(SDData.SDDataBlock(0xFD, dataZip.copyOfRange(10, dataZip.size - 10)))
            else -> throw IllegalStateException("Not expected: " + part)
        }
    }

    private fun handleGetStatus(address: Byte) {
        LOG.info("Mocking status for address ({})", address)
        when (address) {
            0x01.toByte() -> sendEvent(
                AddressStatus(
                    address,
                    0xff.toByte(),
                    byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00)
                )
            )
            else -> throw IllegalStateException("Not expected: " + Common.byteToHex(address))
        }
    }

    private fun prepareSDData() {

        val dataJson = SdDataJson(
            version = "01.02",
            serialNumber = "12345678",
            outputs = listOf(outOnOff1),
            places = emptyList()
        )

        val bytes = Common.OBJECT_MAPPER.writeValueAsBytes(dataJson)

        ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zipOut ->
                val zipEntry = ZipEntry("JSONData.tmp")
                zipOut.putNextEntry(zipEntry)
                zipOut.write(bytes)
            }
            dataZip = out.toByteArray()
        }
    }

    fun startAndWaitForReady(): CompletableDeferred<Unit> {
        val waiting = CompletableDeferred<Unit>()
        stateHandler = { state ->
            if (state == Controller.State.READY) {
                waiting.complete(Unit)
            }
        }
        exceptionHandler = { exception ->
            run {
                waiting.completeExceptionally(exception)
            }
        }
        controller.start()
        return waiting
    }

    init {
        "SunnyDay" {
            LOG.info("OK")
            val waiting = CompletableDeferred<Unit>()
            sunny {
                controller.close()
                waiting.complete(Unit)
            }
            waiting.await()
        }
        "LoginError" {
            loginOK = false
            val cf = startAndWaitForReady()
            val ex = assertThrows<LoginException> { runBlocking { cf.await() } }
            assertThat(ex.message, equalTo("Login error"))
        }
        "InvalidSerial" {
            serial = "050607"
            val cf = startAndWaitForReady()
            val ex = assertThrows<InvalidSerialException> { runBlocking { cf.await() } }
            assertThat(ex.message, equalTo("Invalid serial, got 050607 expected 010203"))
        }
        "SunnyDayWithEvents" {
            LOG.info("OK")
            controller.use {
                val waiting = CompletableDeferred<Unit>()
                sunny {
                    runBlocking {
                        LOG.info("V1")
                        assertThat(controller.dataHandler!!.data.outputs[outOnOff1.id]!!.value, equalTo(0xff.toByte()))

                        LOG.info("V2")
                        sendEvent(
                            Event(outOnOff1.address.toByte(), byteArrayOf(0x00, 0x00, 0x00, 0x00))
                        ).join()

                        LOG.info("V3")
                        assertThat(controller.dataHandler!!.data.outputs[outOnOff1.id]!!.value, equalTo(0x00.toByte()))

                        LOG.info("V4")
                        sendEvent(
                            Event(outOnOff1.address.toByte(), byteArrayOf(0xff.toByte(), 0x00, 0x00, 0x00))
                        ).join()
                        LOG.info("V5")
                        assertThat(controller.dataHandler!!.data.outputs[outOnOff1.id]!!.value, equalTo(0xFF.toByte()))
                        LOG.info("V6")
                        controller.close()
                        waiting.complete(Unit)
                    }
                }
                waiting.await()
            }
        }
        "SunnyDayWithRelogin" {
            LOG.info("OK")
            val waiting = CompletableDeferred<Unit>()
            var readyCount = 0
            controller.use {
                sunny {
                    readyCount++
                    if (readyCount == 1) {
                        sendEvent(Relogin())
                    }
                    else {
                        waiting.complete(Unit)
                    }
                }
            }
            waiting.await()
        }
    }

    private fun sunny(ready: () -> Unit) {
        stateHandler = { state ->
            if (state == Controller.State.READY) {
                ready.invoke()
            }
        }
        controller.start()
    }
}
