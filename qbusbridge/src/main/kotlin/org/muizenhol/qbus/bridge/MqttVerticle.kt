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
import org.muizenhol.homeassistant.discovery.Discovery
import org.muizenhol.homeassistant.discovery.component.Component
import org.muizenhol.homeassistant.discovery.component.Light
import org.muizenhol.homeassistant.discovery.component.Switch
import org.muizenhol.qbus.bridge.type.*
import org.muizenhol.qbus.sddata.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.regex.Pattern

class MqttVerticle(val mqttHost: String, val mqttPort: Int) : AbstractVerticle() {
    lateinit var mqttClient: MqttClient
    lateinit var consumer: MessageConsumer<MqttItemWrapper>
    lateinit var consumerInfo: MessageConsumer<SdDataStruct>
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
        consumerInfo = vertx.eventBus().localConsumer(ADDRESS_INFO, this::handleInfo)
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

    /**
     * Qbus to MQTT
     */
    private fun handle(msg: Message<MqttItemWrapper>) {
        if (!started) {
            LOG.debug("Got msg but MQTT is not yet started")
            return
        }
        val res = publish(msg.body())
        msg.reply(res)
    }

    data class Info(val id: Int, val name: String)

    private fun handleInfo(msg: Message<SdDataStruct>) {
        if (!started) {
            LOG.warn("Got info msg but MQTT is not yet started")
            return
        }
        LOG.info("Sending info on MQTT")
        val item = msg.body()

        item.outputs.values.map { x -> MqttType.fromQbus(x) }
            .distinct().forEach { type ->
                val items = item.outputs.values
                    .filter { i -> MqttType.fromQbus(i) == type }
                    .map { i -> Info(i.id, i.name) }
                    .toList()
                mqttClient.publish(
                    "qbus/" + item.serialNumber + "/info/outputs/" + type.mqttName,
                    Buffer.buffer(OBJECT_MAPPER.writeValueAsBytes(items)),
                    MqttQoS.AT_LEAST_ONCE,
                    false,
                    true
                ) { ar ->
                    if (ar.failed()) {
                        LOG.warn("Can't send MQTT message, restarting connection", ar.cause())
                        restart()
                    } else {
                        LOG.debug("MQTT publish OK")
                    }
                }
            }

        makeHomeAssistantDiscovery(item)

        msg.reply(MqttHandled.OK)
    }

    private fun makeHomeAssistantDiscovery(data: SdDataStruct) {
        LOG.info("Sending homassistant discovery")
        data.outputs.forEach { (x, output) ->
            val uuid = "qbus-${data.serialNumber}-${output.id}"
            val type = MqttType.fromQbus(output)
            val topicPrefix = "qbus/${data.serialNumber}/sensor/${type.mqttName}/${output.id}"
            val stateTopic = "${topicPrefix}/state"
            val commandTopic = "${topicPrefix}/command"
            val device: Component = when (output) {
                is SdOutputOnOff -> Switch(
                    output.name,
                    uuid,
                    commandTopic,
                    stateTopic,
                    "255",
                    "0"
                )

                is SdOutputDimmer -> Light.Builder()
                    .withName(output.name)
                    .withUniqueId(uuid)
                    .withCommandTopic(commandTopic)
                    .withStateTopic("${topicPrefix}/actualState")
                    .withPayloadOn("ON")
                    .withPayloadOff("OFF")
                    .withBrightnessScale(255)
                    .withBrightnessStateTopic(stateTopic)
                    .withBrightnessCommandTopic(commandTopic)
                    .withOnCommandType("brightness")
                    .build()

                else -> {
                    LOG.warn("Ignoring {} in homeAssitant discovery", output.javaClass)
                    return
                }
            }
            val devices = mapOf(
                uuid to device
            )


            val discovery = Discovery(
                Discovery.Device(
                    uuid,
                    "mouse256",
                    "qbus",
                    output.name
                ),
                Discovery.Origin(
                    "qbus-mqtt"
                ),
                "not/used",
                devices
            )


            //homeassistant/device/alfen-mqtt-dev/ACE0403792-1/config
            mqttClient.publish(
                "homeassistant/device/qbus-mqtt/${uuid}/config",
                Buffer.buffer(OBJECT_MAPPER.writeValueAsBytes(discovery)),
                MqttQoS.AT_LEAST_ONCE,
                false,
                true //retain
            ) { ar ->
                if (ar.failed()) {
                    LOG.warn("Can't send MQTT message, restarting connection", ar.cause())
                    restart()
                } else {
                    LOG.debug("MQTT publish OK")
                }
            }
        }

    }

    private val states = mutableMapOf<String, MutableMap<Int, State>>()

    data class State(val type: String, val payload: String, val name: String, val place: String)

    private fun publish(item: MqttItemWrapper): MqttHandled {
        //TODO: fix
        /*
        val map = states.computeIfAbsent(item.serial) { mutableMapOf() }
        map.put(data.id, State(data.typeName, payload, data.name, data.place.name))*/

        val data = item.data
        return when (data) {
            is SdOutputOnOff -> publish(item, data.asInt().toString(), "state")
            is SdOutputDimmer -> {
                publish(item, if (data.asInt() > 0) "ON" else "OFF", "actualState")
                publish(item, data.asInt().toString(), "state")
            }

            is SdOutputTimer -> publish(item, data.asInt().toString(), "state")
            is SdOutputTimer2 -> publish(item, data.asInt().toString(), "state")
            is SdOutputThermostat -> {
                publish(item, data.getTempSet().toString(), "set")
                publish(item, data.getTempMeasured().toString(), "measured")
            }

            is SdOutputAudio -> {
                publish(item, data.asInt().toString(), "event", false)
            }

            is SdOutputAudioGroup -> MqttHandled.OK
        }
    }

    private fun publish(item: MqttItemWrapper, payloadS: String, topic: String, retain: Boolean = true): MqttHandled {
        val type = MqttType.fromQbus(item.data)
        val payload = Buffer.buffer(payloadS)

        mqttClient.publish(
            "qbus/" + item.serial + "/sensor/" + type.mqttName + "/" + item.data.id + "/${topic}",
            payload,
            MqttQoS.AT_LEAST_ONCE,
            false,
            retain
        ) { ar ->
            if (ar.failed()) {
                LOG.warn("Can't send MQTT message, restarting connection", ar.cause())
                restart()
            } else {
                LOG.debug("MQTT publish OK")
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
                LOG.debug("MQTT publish OK")
            }
        }
        return MqttHandled.OK
    }

    private fun subscribe() {
        mqttClient.publishHandler(this::handleOpenhabEvent)
        mqttClient.subscribe(
            "qbus/+/sensor/+/+/command",
            MqttQoS.AT_LEAST_ONCE.value()
        )
        mqttClient.subscribe(
            "/airquality/+/sensor/+",
            MqttQoS.AT_LEAST_ONCE.value()
        )
    }

    private fun handleOpenhabEvent(msg: MqttPublishMessage) {
        LOG.debug("Got msg on {}", msg.topicName())
        val matcher = PATTERN_COMMAND.matcher(msg.topicName())
        if (matcher.matches()) {
            val serial = matcher.group(1)
            val type = matcher.group(2)
            val id = matcher.group(3).toInt()
            val payload = msg.payload().toString(StandardCharsets.UTF_8)
            LOG.debug("Got {} data for {}: {}", type, id, payload)
            toQbus(serial, type, payload, id)
        } else {
            val matcher2 = PATTERN_AIRQUALITY.matcher(msg.topicName())
            if (matcher2.matches()) {
                val name = matcher2.group(1)
                val sensor = matcher2.group(2)
                msg.payload().toString(StandardCharsets.UTF_8).toDoubleOrNull()?.let { data ->
                    vertx.eventBus().publish(ADDRESS_SENSOR, MqttSensorItem(name, sensor, data))
                }
            } else {
                LOG.debug("Topic not matched: {}", msg.topicName())
            }
        }
    }

    private fun toQbus(serial: String, typeS: String, payload: String, id: Int) {
        val type = MqttType.fromMqtt(typeS)
        val placeDummy = SdDataStruct.Place(-1, "")
        if (type == null) {
            LOG.debug("Unsupported type: {}", type)
        } else {
            val data: SdOutput = when (type) {
                MqttType.ON_OFF -> {
                    SdOutputOnOff(id, "", 0x00, 0x00, -1, placeDummy, false).apply {
                        val pay = convertPayloadOnOff(payload) ?: return
                        value = pay
                    }
                }

                MqttType.DIMMER ->
                    SdOutputDimmer(id, "", 0x00, 0x00, -1, placeDummy, false).apply {
                        val pay = convertPayloadDimmer(payload) ?: return
                        value = pay
                    }

                MqttType.THERMOSTAT ->
                    SdOutputThermostat(id, "", 0x00, -1, placeDummy, false).apply {
                        val pay = convertThermostatPayload(payload) ?: return
                        setTemp(pay)
                    }

                MqttType.EVENT -> {
                    LOG.warn("Event can't be translated to Qbus")
                    return
                }
            }
            LOG.debug("Sending to Qbus verticle")
            vertx.eventBus().send(QbusVerticle.ADDRESS_UPDATE_QBUS_ITEM, MqttItemWrapper(serial, data))
        }

    }

    private fun convertPayloadOnOff(payload: String): Byte? {
        return when (payload) {
            "0" -> 0x00.toByte()
            "255" -> 0xFF.toByte()
            else -> {
                LOG.warn("Invalid ON_OFF payload: {}", payload)
                null
            }
        }
    }

    private fun convertPayloadDimmer(payload: String): Byte? {
        if (payload == "ON") {
            return null //ignore, there will be a brightess command
        }
        if (payload == "OFF") {
            return 0x00.toByte()
        }
        val p = payload.toDoubleOrNull()
            ?.toInt()
            ?.takeIf { i -> i in 0..255 }
            ?.toByte()
        if (p != null) {
            return p
        } else {
            LOG.warn("Invalid dimmer payload: {}", payload)
            return null
        }
    }

    private fun convertThermostatPayload(payload: String): Double? {
        val p = payload.toDoubleOrNull()
            ?.takeIf { i -> (0 < i && i <= 30) }
        if (p != null) {
            return p
        } else {
            LOG.warn("Invalid thermostat payload: {}", payload)
            return null
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PATTERN_COMMAND = Pattern.compile("qbus/([^/]+)/sensor/([^/]+)/(\\d+)/command")
        private val PATTERN_AIRQUALITY = Pattern.compile("^/airquality/([^/]+)/sensor/([^/]+)$")
        val ADDRESS = "address_mqtt_verticle"
        val ADDRESS_SENSOR = "address_mqtt_sensor"
        val ADDRESS_INFO = "address_mqtt_info"
        private val OBJECT_MAPPER = ObjectMapper()
    }

}