package org.muizenhol.qbus.bridge

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.MqttClientOptions
import io.vertx.mqtt.messages.MqttPublishMessage
import org.muizenhol.qbus.bridge.type.MqttHandled
import org.muizenhol.qbus.bridge.type.MqttItem
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.regex.Pattern
import kotlin.math.round

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
            vertx.eventBus().send(QbusVerticle.ADDRESS_STATUS, StatusRequest.SEND_ALL_STATES)
        }
        consumer = vertx.eventBus().localConsumer(ADDRESS, this::handle)
        mqttClient.closeHandler {
            LOG.info("Mqtt closed, restart")
            restart()
        }
        mqttClient.exceptionHandler { ex ->
            LOG.warn("Exception", ex)
            restart()
        }
    }

    override fun stop() {
        LOG.info("Stopping")
        started = false
        consumer.unregister()
        if (mqttClient.isConnected) {
            mqttClient.disconnect()
        }
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
        val res = sendToMqtt(msg.body())
        msg.reply(res)
    }

    /**
     * Qbus to MQTT
     */
    private fun sendToMqtt(item: MqttItem): MqttHandled {
        LOG.info("update for {} to {}", item.id, item.payload)
        if (!mqttClient.isConnected) {
            LOG.warn("MQTT is not connected, ignoring")
            return MqttHandled.MQTT_ERROR
        }
        when (item.type) {
            MqttItem.Type.ON_OFF -> {
                when (item.payload) {
                    0x00 -> "0"
                    0xFF -> "255"
                    else -> {
                        LOG.warn("Invalid payload for ON_OFF type. Sensor {}: {}", item.id, item.payload)
                        return MqttHandled.DATA_ERROR
                    }
                }.let { v ->
                    LOG.info("MQTT Publish switch to {}", v)
                    publish(item,  "switch", v)
                    return MqttHandled.OK
                }
            }
            MqttItem.Type.DIMMER -> {
                val v = item.payload.toString()
                LOG.info("MQTT Publish dimmer to {}", v)
                publish(item, "dimmer", v)
                return MqttHandled.OK
            }
            else -> {
                //TODO add support for more types
                return MqttHandled.DATA_TYPE_NOT_SUPPORTED
            }
        }
    }

    private val states = mutableMapOf<String, MutableMap<Int, State>>()
    data class State(val type: String, val payload: String, val name: String, val place: String)

    private fun publish(item: MqttItem, type: String, payload: String) {
        states.computeIfAbsent(item.serial) { mutableMapOf() }
        states[item.serial]?.put(item.id, State(type, payload, item.name, item.place))

        mqttClient.publish(
            "qbus/" + item.serial + "/sensor/" + type + "/" + item.id + "/state",
            Buffer.buffer(payload),
            MqttQoS.AT_LEAST_ONCE,
            false,
            true
        ) { ar ->
            if (ar.failed()) {
                LOG.warn("Can't send MQTT message, restarting connection", ar.cause())
                restart()
            } else {
                LOG.info("MQTT publish OK")
            }
        }
        mqttClient.publish(
            "qbus/" + item.serial + "/state",
            Buffer.buffer(OBJECT_MAPPER.writeValueAsBytes(states)),
            MqttQoS.AT_LEAST_ONCE,
            false,
            true
        ) { ar ->
            if (ar.failed()) {
                LOG.warn("Can't send MQTT message", ar.cause())
            } else {
                LOG.info("MQTT publish OK")
            }
        }
    }

    private fun subscribe() {
        mqttClient.publishHandler(this::handleOpenhabEvent)
        mqttClient.subscribe(
            "qbus/+/sensor/+/+/command",
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
            toQbus(serial, type, payload, id)
        } else {
            LOG.info("Topic not matched")
        }
    }

    private fun toQbus(serial: String, type: String, payload: String, id: Int) {
        MqttItem.Type.fromMqtt(type)?.let { t ->
            t.toQbusInternal(payload)?.let { p ->
                val item = MqttItem(serial, t, id, p, "", "")
                LOG.debug("Sending to Qbus verticle")
                vertx.eventBus().send(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM, item)
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PATTERN_COMMAND = Pattern.compile("qbus/([^/]+)/sensor/([^/]+)/(\\d+)/command")
        val ADDRESS = "address_mqtt_verticle"
        private val OBJECT_MAPPER = ObjectMapper()
    }

}