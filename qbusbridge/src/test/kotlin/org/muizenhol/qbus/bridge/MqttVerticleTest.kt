package org.muizenhol.qbus.bridge

import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.AsyncResult
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.muizenhol.qbus.bridge.type.MqttHandled
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.muizenhol.qbus.sddata.SdDataStruct
import org.muizenhol.qbus.sddata.SdOutputDimmer
import org.muizenhol.qbus.sddata.SdOutputOnOff
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
@Timeout(10, timeUnit = TimeUnit.SECONDS)
class MqttVerticleTest() {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    var mqttClient: MqttClient? = null
    var mqttServer: StubbedMqttServer? = null
    var port: Int = 0
    val place = SdDataStruct.Place(-1, "")

    fun startListener(vertx: Vertx, vertxContext: VertxTestContext) {
        val checkpoint = vertxContext.checkpoint()
        val mqttClientOptions = MqttClientOptions()
            .setClientId("Junit")
        mqttClient = MqttClient.create(vertx, mqttClientOptions)
        mqttClient?.connect(port, "localhost") { ar ->
            if (ar.succeeded()) {
                checkpoint.flag()
            } else {
                LOG.error("Can't create junit client")
                vertxContext.failNow(ar.cause())
            }
        }
    }

    @BeforeEach
    fun beforeEach(vertx: Vertx, vertxContext: VertxTestContext) {
        LocalOnlyCodec.register(vertx, MqttItemWrapper::class.java)
        LocalOnlyCodec.register(vertx, StatusRequest::class.java)
        LocalOnlyCodec.register(vertx, MqttHandled::class.java)
        LocalOnlyCodec.register(vertx, SdDataStruct::class.java)
        mqttServer = StubbedMqttServer()
        mqttServer?.setupMqttServer(vertx, vertxContext) { port ->
            this.port = port
            startListener(vertx, vertxContext)
        }
    }

    @AfterEach
    fun afterEach(vertx: Vertx, vestContext: VertxTestContext) {
        val checkpoint = vestContext.checkpoint()
        mqttServer?.close { vertx.close { checkpoint.flag() } }
    }

    fun start(vertx: Vertx, vertxContext: VertxTestContext, whenStarted: () -> Unit) {
        val checkpoint = vertxContext.checkpoint()
        val startPromise = Promise.promise<String>()
        val mqttVerticle = MqttVerticle("localhost", port)
        vertx.deployVerticle(mqttVerticle, startPromise)
        startPromise.future().onFailure { t -> vertxContext.failNow(t) }
        startPromise.future().onSuccess {
            LOG.debug("mqttVerticle started")
        }

        vertx.eventBus().localConsumer<StatusRequest>(QbusVerticle.ADDRESS_STATUS) {
            LOG.debug("mqttVerticle mqtt consumer ready")
            try {
                whenStarted.invoke()
                checkpoint.flag()
            } catch (ex: Exception) {
                vertxContext.failNow(ex)
            }
        }
    }

    fun expectMqtt(vertxContext: VertxTestContext, topic: String, payload: String?) {
        val checkpoint = vertxContext.checkpoint()
        mqttClient!!.publishHandler { msg ->
            val value = msg.payload().toString(StandardCharsets.UTF_8)
            LOG.info("Got on {}: \"{}\"", msg.topicName(), value)
            if (msg.topicName().equals(topic)) {
                LOG.info("topic OK")
                if (payload == null || value.equals(payload)) {
                    checkpoint.flag()
                } else {
                    LOG.debug("Not matched: \"{}\" != \"{}\"", value, payload)
                }
            }

        }
        mqttClient!!.subscribe("qbus/#", MqttQoS.AT_LEAST_ONCE.value()) { ar ->
            if (ar.succeeded()) {
                LOG.info("Subscribe OK")
            } else {
                vertxContext.failNow(ar.cause())
            }
        }
    }

    private fun send(vertx: Vertx, vertxContext: VertxTestContext, item: MqttItemWrapper, expected: MqttHandled) {
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().request(MqttVerticle.ADDRESS, item) { ar: AsyncResult<Message<MqttHandled>> ->
            if (ar.failed()) {
                vertxContext.failNow(ar.cause())
            } else {
                val res = ar.result().body()
                if (res != expected) {
                    vertxContext.failNow(IllegalStateException("Not expected result: ${res}"))
                } else {
                    checkpoint.flag()
                }
            }
        }
    }

    private fun mkSwitch(serial: String, id: Int, payload: Byte, readOnly: Boolean = false): MqttItemWrapper {
        return MqttItemWrapper(
            serial,
            SdOutputOnOff(id, "", 0x00, 0x00, -1, place, readOnly).apply {
                value = payload
            })
    }

    private fun mkDimmer(serial: String, id: Int, payload: Byte): MqttItemWrapper {
        return MqttItemWrapper(
            serial,
            SdOutputDimmer(id, "", 0x00, 0x00, -1, place, false).apply {
                value = payload
            })
    }

    @Test
    fun testSwitchOff(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/sensor/switch/1/state", "0")
            val item = mkSwitch("12345", 1, 0x00.toByte())
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testSwitchOn(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/54321/sensor/switch/2/state", "255")
            val item = mkSwitch("54321", 2, 0xFF.toByte())
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    @Disabled //rather useless, we can expect data coming from qbus to be valid
    fun testSwitchInvalid(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            val item = mkSwitch("54321", 2, 0x33.toByte())
            send(vertx, vertxContext, item, MqttHandled.DATA_ERROR)
        }
    }

    @Test
    fun testSwitchOnReadonly(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/54321/sensor/switch/2/state", "255")
            val item = mkSwitch("54321", 2, 0xFF.toByte(), readOnly = true)
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testDimmerOff(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/sensor/dimmer/1/state", "0")
            val item = mkDimmer("12345", 1, 0x00.toByte())
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testDimmerOn(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/54321/sensor/dimmer/2/state", "255")
            val item = mkDimmer("54321", 2, 0xFF.toByte())
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testDimmer(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/sensor/dimmer/1/state", "187")
            val item = mkDimmer("12345", 1, 0xBB.toByte())
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    private fun expectItem(vertx: Vertx, vertxContext: VertxTestContext, expected: MqttItemWrapper) {
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().localConsumer(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM) { ar: Message<MqttItemWrapper> ->
            try {
                assertThat(ar.body(), equalTo(expected))
                assertThat(ar.body().data.printValue(), equalTo(expected.data.printValue()))
                checkpoint.flag()
            } catch (t: Throwable) {
                vertxContext.failNow(t)
            }
        }
    }

    @Test
    fun testDimmerCommandOff(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = mkDimmer("12345", 1, 0x00.toByte())
        expectItem(vertx, vertxContext, expected)
        start(vertx, vertxContext) {
            mqttClient!!.publish(
                "qbus/12345/sensor/dimmer/1/command",
                Buffer.buffer("0"), MqttQoS.AT_LEAST_ONCE, false, false
            )
        }
    }

    @Test
    fun testDimmerCommandOn(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = mkDimmer("12345", 1, 0xFF.toByte())
        expectItem(vertx, vertxContext, expected)
        start(vertx, vertxContext) {
            mqttClient!!.publish(
                "qbus/12345/sensor/dimmer/1/command",
                Buffer.buffer("255"), MqttQoS.AT_LEAST_ONCE, false, false
            )
        }
    }

    @Test
    fun testDimmerCommandHalf(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = mkDimmer("12345", 1, 123.toByte())
        expectItem(vertx, vertxContext, expected)
        start(vertx, vertxContext) {
            mqttClient!!.publish(
                "qbus/12345/sensor/dimmer/1/command",
                Buffer.buffer("123.45"), MqttQoS.AT_LEAST_ONCE, false, false
            )
        }
    }

    @Test
    fun testSwitchCommandOn(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = mkSwitch("12345", 1, 0xFF.toByte())
        expectItem(vertx, vertxContext, expected)
        start(vertx, vertxContext) {
            mqttClient!!.publish(
                "qbus/12345/sensor/switch/1/command",
                Buffer.buffer("255"), MqttQoS.AT_LEAST_ONCE, false, false
            )
        }
    }

    @Test
    fun testSwitchCommandInvalid(vertx: Vertx, vertxContext: VertxTestContext) {
        val checkpoint = vertxContext.checkpoint()
        vertx.setTimer(1000) { checkpoint.flag() }
        vertx.eventBus().localConsumer(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM) { _: Message<MqttItemWrapper> ->
            vertxContext.failNow(IllegalStateException("no data expected"))
        }
        start(vertx, vertxContext) {
            mqttClient!!.publish(
                "qbus/12345/sensor/switch/1/command",
                Buffer.buffer("55"), MqttQoS.AT_LEAST_ONCE, false, false
            )
        }
    }

    private fun sendInfo(vertx: Vertx, vertxContext: VertxTestContext, item: SdDataStruct, expected: MqttHandled) {
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().request(MqttVerticle.ADDRESS_INFO, item) { ar: AsyncResult<Message<MqttHandled>> ->
            if (ar.failed()) {
                vertxContext.failNow(ar.cause())
            } else {
                val res = ar.result().body()
                if (res != expected) {
                    vertxContext.failNow(IllegalStateException("Not expected result: ${res}"))
                } else {
                    checkpoint.flag()
                }
            }
        }
    }

    @Test
    fun testHomeAssitantDiscovery(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "homeassistant/device/qbus-mqtt/device1/config", null)
            val data = SdDataStruct(
                "dummyVersion", "dummySerial",
                mapOf(1 to SdDataStruct.Place(11, "dummyPlace")),
                mapOf(1 to SdOutputOnOff(123, "device1", 0x01, 0x00, -1, place, false)
                )
            )
            sendInfo(vertx, vertxContext, data, MqttHandled.OK)
        }
    }

}
