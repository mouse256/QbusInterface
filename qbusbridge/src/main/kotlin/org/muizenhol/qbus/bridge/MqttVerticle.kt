package org.muizenhol.qbus.bridge

import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import io.vertx.mqtt.messages.MqttPublishMessage
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.regex.Pattern

class MqttVerticle(val mqttHost: String, val mqttPort: Int) : AbstractVerticle() {
    lateinit var mqttClient: MqttClient
    lateinit var consumer: MessageConsumer<MqttItem>
    var started = false

    override fun start() {
        LOG.info("Verticle starting")
        val mqttClientOptions = MqttClientOptions()
            .setMaxInflightQueue(200)
        mqttClient = MqttClient.create(vertx, mqttClientOptions)
        connectMqtt {
            LOG.info("MQTT ready")
            started = true
            subscribe()
            //when mqtt is started, ask Qbus to send all states
            vertx.eventBus().send(QbusVerticle.ADDRESS_STATUS, QbusVerticle.StatusRequest.SEND_ALL_STATES)
        }
        consumer = vertx.eventBus().localConsumer(ADDRESS, this::handle)
        mqttClient.closeHandler {
            LOG.info("Mqtt closed, restart")
            restart()
        }
        mqttClient.exceptionHandler{ex ->
            LOG.warn("Exception", ex)
            restart()
        }
    }

    override fun stop() {
        LOG.info("Stopping")
        started = false
        consumer.unregister()
        mqttClient.disconnect()
    }

    private fun restart() {
        if (!started) {
            LOG.warn("Cannot restart, not yet running")
            return
        }
        stop()
        LOG.info("Restarting in 30s")
        vertx.setTimer(Duration.ofSeconds(30).toMillis()) { start() }
    }


    private fun connectMqtt(onConnected: () -> Unit = {}) {
        mqttClient.connect(mqttPort, mqttHost) { ar ->
            if (ar.failed()) {
                LOG.warn("MQTT connection failed, retrying in 60 s", ar.cause())
                vertx.setTimer(Duration.ofSeconds(60).toMillis()) { connectMqtt(onConnected) }
            } else {
                LOG.info("MQTT connected")
                onConnected.invoke()
            }
        }
    }

    private fun handle(msg: Message<MqttItem>) {
        if (!started) {
            LOG.debug("Got msg but MQTT is not yet started")
            return
        }
        sendToMqtt(msg.body().serial, msg.body().data)
    }

    /**
     * Qbus to MQTT
     */
    private fun sendToMqtt(serial: String, data: SdDataStruct.Output) {
        LOG.info("update for {} to {}", data.name, data.value)
        if (!mqttClient.isConnected) {
            LOG.warn("MQTT is not connected, ignoring")
            return
        }
        if (data.type == SdDataStruct.Type.ON_OFF) {
            //TODO add support for more types
            when (data.value) {
                0x00.toByte() -> "OFF"
                0xFF.toByte() -> "ON"
                else -> null
            }?.let { v ->
                LOG.info("MQTT Publish switch to {}", v)
                publish(serial, data, "switch", Buffer.buffer(v))
            }
        }
    }

    private fun publish(serial: String, data: SdDataStruct.Output, type: String, payload: Buffer) {
        mqttClient.publish(
            "qbus/" + serial + "/" + type + "/" + data.id + "/state",
            payload,
            MqttQoS.AT_LEAST_ONCE,
            false,
            true
        ) { ar ->
            if (ar.failed()) {
                LOG.warn("Can't send MQTT message, restarting connection", ar.cause())
                restart()
            }
        }
    }

    private fun subscribe() {
        mqttClient.publishHandler(this::handleOpenhabEvent)
        mqttClient.subscribe(
            "qbus/+/+/+/command",
            MqttQoS.AT_LEAST_ONCE.value()
        )
    }

    private fun handleOpenhabEvent(msg: MqttPublishMessage) {
        LOG.info("Got msg on {}", msg.topicName())
        val matcher = PATTERN_COMMAND.matcher(msg.topicName())
        if (matcher.matches()) {
            val serial = matcher.group(1)
            val type = matcher.group(2)
            val id = matcher.group(3).toInt()
            val payload = msg.payload().toString(StandardCharsets.UTF_8)
            LOG.info("Got {} data for {}: {}", type, id, payload)
            val item = MqttReceiveItem(serial, type, id, payload)
            vertx.eventBus().send(QbusVerticle.ADDRESS, item)
        } else {
            LOG.info("Topic not matched")
        }
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PATTERN_COMMAND = Pattern.compile("qbus/([^/]+)/([^/]+)/(\\d+)/command")
        val ADDRESS = "address_mqtt_verticle"
    }

    data class MqttItem(val serial: String, val data: SdDataStruct.Output)
    data class MqttReceiveItem(val serial: String, val type: String, val id: Int, val payload: String)
}