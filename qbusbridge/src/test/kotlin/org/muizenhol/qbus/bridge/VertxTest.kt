package org.muizenhol.qbus.bridge

import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.muizenhol.qbus.bridge.type.MqttHandled
import org.muizenhol.qbus.bridge.type.MqttItem
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
@Timeout(10, timeUnit = TimeUnit.SECONDS)
class VertxTest() {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    var mqttClient: MqttClient? = null
    var mqttServer: StubbedMqttServer? = null
    var port: Int = 0

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
        LocalOnlyCodec.register(vertx, MqttItem::class.java)
        LocalOnlyCodec.register(vertx, StatusRequest::class.java)
        LocalOnlyCodec.register(vertx, MqttHandled::class.java)
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

    fun expectMqtt(vertxContext: VertxTestContext, topic: String, payload: String) {
        val checkpoint = vertxContext.checkpoint()
        mqttClient!!.publishHandler { msg ->
            val value = msg.payload().toString(StandardCharsets.UTF_8)
            LOG.info("Got on {}: \"{}\"", msg.topicName(), value)
            if (msg.topicName().equals(topic)) {
                LOG.info("topic OK")
                if (value.equals(payload)) {
                    checkpoint.flag()
                }
                else {
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

    private fun send(vertx: Vertx, vertxContext: VertxTestContext, item: MqttItem, expected: MqttHandled) {
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().request(MqttVerticle.ADDRESS, item) { ar: AsyncResult<Message<MqttHandled>> ->
            if (ar.failed()) {
                vertxContext.failNow(ar.cause())
            }
            else {
                val res = ar.result().body()
                if (res != expected) {
                    vertxContext.failNow(IllegalStateException("Not expected result: ${res}"))
                }
                else {
                    checkpoint.flag()
                }
            }
        }
    }

    @Test
    fun testSwitchOff(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/switch/1/state", "0")
            val item = MqttItem("12345", MqttItem.Type.ON_OFF, 1, 0x00)
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testSwitchOn(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/54321/switch/2/state", "255")
            val item = MqttItem("54321", MqttItem.Type.ON_OFF, 2, 0xFF)
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testSwitchInvalid(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            val item = MqttItem("54321", MqttItem.Type.ON_OFF, 2, 0x33)
            send(vertx, vertxContext, item, MqttHandled.DATA_ERROR)
        }
    }

    @Test
    fun testDimmerOff(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/dimmer/1/state", "0")
            val item = MqttItem("12345", MqttItem.Type.DIMMER, 1, 0x00)
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testDimmerOn(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/dimmer/1/state", "255")
            val item = MqttItem("12345", MqttItem.Type.DIMMER, 1, 0xFF)
            LOG.info("test: {}", item.payload)
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testDimmer(vertx: Vertx, vertxContext: VertxTestContext) {
        start(vertx, vertxContext) {
            expectMqtt(vertxContext, "qbus/12345/dimmer/1/state", "187")
            val item = MqttItem("12345", MqttItem.Type.DIMMER, 1, 0xBB)
            LOG.info("test: {}", item.payload)
            send(vertx, vertxContext, item, MqttHandled.OK)
        }
    }

    @Test
    fun testDimmerCommandOff(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = MqttItem("12345", MqttItem.Type.DIMMER, 1, 0)
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().localConsumer(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM) {ar: Message<MqttItem> ->
            if (!expected.equals(ar.body())) {
                vertxContext.failNow(IllegalArgumentException("not expected: " + ar.body()))
            }
            checkpoint.flag()
        }
        start(vertx, vertxContext) {
            mqttClient!!.publish("qbus/12345/dimmer/1/command",
                Buffer.buffer("0"), MqttQoS.AT_LEAST_ONCE, false, false)
        }
    }

    @Test
    fun testDimmerCommandOn(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = MqttItem("12345", MqttItem.Type.DIMMER, 1, 255)
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().localConsumer(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM) {ar: Message<MqttItem> ->
            if (!expected.equals(ar.body())) {
                vertxContext.failNow(IllegalArgumentException("not expected: " + ar.body()))
            }
            checkpoint.flag()
        }
        start(vertx, vertxContext) {
            mqttClient!!.publish("qbus/12345/dimmer/1/command",
                Buffer.buffer("255"), MqttQoS.AT_LEAST_ONCE, false, false)
        }
    }

    @Test
    fun testDimmerCommandHalf(vertx: Vertx, vertxContext: VertxTestContext) {
        val expected = MqttItem("12345", MqttItem.Type.DIMMER, 1, 123)
        val checkpoint = vertxContext.checkpoint()
        vertx.eventBus().localConsumer(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM) {ar: Message<MqttItem> ->
            if (!expected.equals(ar.body())) {
                vertxContext.failNow(IllegalArgumentException("not expected: " + ar.body()))
            }
            checkpoint.flag()
        }
        start(vertx, vertxContext) {
            mqttClient!!.publish("qbus/12345/dimmer/1/command",
                Buffer.buffer("123.45"), MqttQoS.AT_LEAST_ONCE, false, false)
        }
    }

}
