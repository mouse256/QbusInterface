package org.muizenhol.qbus.bridge

import io.netty.handler.codec.mqtt.MqttQoS
import io.quarkus.runtime.Startup
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.mqtt.MqttClient
import io.vertx.mqtt.messages.MqttPublishMessage
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped

@Startup
@ApplicationScoped
class ControllerHandler {
    var dataHandler: DataHandler? = null
    lateinit var controller: Controller
    lateinit var mqttClient: MqttClient
    lateinit var mqttHost: String
    var mqttPort: Int = -1
    var reconnecting = false


    @PostConstruct
    fun create() {
        LOG.info("creating vertx")

        val vertx = Vertx.vertx()
        mqttClient = MqttClient.create(vertx);


        val prop = Properties()
        val file = File(System.getenv("QBUS_PROPERTY_FILE") ?: "/tmp/qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val username = getOrThrow(prop, "username")
        val password = getOrThrow(prop, "password")
        val serial = getOrThrow(prop, "serial")
        val host = getOrThrow(prop, "host")

        mqttHost = getOrThrow(prop, "mqtt.host")
        mqttPort = prop.getOrDefault("mqtt.port", 1883) as Int

        connectMqtt()

        controller = Controller(serial, username, password, host, { ready ->
            dataHandler = ready
            subscribe()
            ready.setEventListener(this::onDataUpdate)

            publishCurrentState()
        })
        controller.run()
    }

    @PreDestroy
    fun destroy() {
        LOG.info("Destroying")
        controller.close()
        mqttClient.disconnect()
    }

    private fun connectMqtt(onConnected: () -> Unit = {}) {
        mqttClient.connect(mqttPort, mqttHost) {
            LOG.info("MQTT connected")
            onConnected.invoke()
        }
    }

    private fun publishCurrentState() {
        //publish current state
        dataHandler!!.data.outputs.values.forEach { out -> onDataUpdate(dataHandler!!.data.serialNumber, out) }
    }

    private fun getOrThrow(prop: Properties, name: String): String {
        return prop.getProperty(name) ?: throw IllegalArgumentException("Can't find property for $name")
    }

    private fun subscribe() {
        dataHandler?.let { dh ->
            mqttClient.publishHandler(this::handleOpenhabEvent)
            mqttClient.subscribe(
                "qbus/" + dh.data.serialNumber + "/+/+/command",
                MqttQoS.AT_LEAST_ONCE.value()
            )
        }
    }

    private fun handleOpenhabEvent(msg: MqttPublishMessage) {
        LOG.info("Got msg on {}", msg.topicName())
        val matcher = PATTERN_COMMAND.matcher(msg.topicName())
        if (matcher.matches()) {
            val type = matcher.group(2)
            val id = matcher.group(3).toInt()
            val payload = msg.payload().toString(StandardCharsets.UTF_8)
            LOG.info("Got {} data for {}: {}", type, id, payload)
            when (type) {
                "switch" -> handleSwitchUpdate(id, payload)
                else -> LOG.warn("Can't handle type {}", type)
            }
        } else {
            LOG.info("Topic not matched")
        }
    }

    private fun handleSwitchUpdate(id: Int, payload: String) {
        val out = dataHandler?.getOutput(id)
        if (out != null) {
            when (payload) {
                "ON" -> 0XFF.toByte()
                "OFF" -> 0X00.toByte()
                else -> null
            }?.let {
                //value -> dataHandler?.update(id, value)
                out.value = it
                controller.setNewState(out)
            }
        } else {
            LOG.warn("can't find output with id {}", id)
        }
    }

    /**
     * Qbus to MQTT
     */
    private fun onDataUpdate(serial: String, data: SdDataStruct.Output) {
        LOG.debug("update for {} to {}", data.name, data.value)
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
        if (reconnecting) {
            LOG.warn("MQTT client is reconnecting, ignoring")
            return
        }
        mqttClient.publish(
            "qbus/" + serial + "/" + type + "/" + data.id + "/state",
            payload,
            MqttQoS.AT_LEAST_ONCE,
            false,
            true
        ) { ar ->
            if (ar.failed()) {
                LOG.warn("Can't send MQTT message, restarting connection", ar.cause())
                reconnecting = true
                mqttClient.disconnect()
                connectMqtt {
                    subscribe()
                    reconnecting = false
                    publishCurrentState()
                }
            }
        }
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PATTERN_COMMAND = Pattern.compile("qbus/([^/]+)/([^/]+)/(\\d+)/command")
    }
}