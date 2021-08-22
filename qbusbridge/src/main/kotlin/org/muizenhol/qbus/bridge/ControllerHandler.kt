package org.muizenhol.qbus.bridge

import io.quarkus.runtime.Startup
import io.vertx.core.AsyncResult
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.bridge.type.MqttHandled
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.bridge.type.MqttSensorItem
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.invoke.MethodHandles
import java.util.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped

@Startup
@ApplicationScoped
class ControllerHandler {
    lateinit var mqttHost: String
    lateinit var vertx: Vertx
    lateinit var qbusVerticle: QbusVerticle
    val verticles = mutableListOf<String>()


    @PostConstruct
    @Suppress("unused")
    fun create() {
        LOG.info("creating vertx")
        vertx = Vertx.vertx()

        val prop = Properties()
        val file = File(System.getenv("QBUS_PROPERTY_FILE") ?: "/tmp/qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val environment = Environment.valueOf(getOrThrow(prop, "environment").toUpperCase())
        val username = getOrThrow(prop, "username")
        val password = getOrThrow(prop, "password")
        val serial = getOrThrow(prop, "serial")
        val host = getOrThrow(prop, "host")
        val influxToken = prop.getProperty("influx.token")
        val influxUrl = prop.getProperty("influx.url")

        mqttHost = getOrThrow(prop, "mqtt.host")
        val mqttPort = prop.getOrDefault("mqtt.port", 1883) as Int

        registerVertxCodecs(vertx)

        val influxVerticle = InfluxVerticle(influxToken, influxUrl)
        vertx.deployVerticle(influxVerticle, DeploymentOptions().setWorker(true))

        val timestreamVerticle = TimestreamVerticle(
            environment,
            prop.getProperty("aws.accessId"), prop.getProperty("aws.accessSecret")
        )
        vertx.deployVerticle(timestreamVerticle, DeploymentOptions().setWorker(true))

        val mqttVerticle = MqttVerticle(mqttHost, mqttPort)
        vertx.deployVerticle(mqttVerticle)

        qbusVerticle = QbusVerticle(username, password, serial, host)
        vertx.deployVerticle(qbusVerticle, this::verticleDeployed)
    }

    fun verticleDeployed(id: AsyncResult<String>) {
        if (id.failed()) {
            LOG.error("Verticle deploy failed", id.cause())
        } else {
            LOG.info("Verticle deployed: {}", id.result())
            verticles.add(id.result())
        }
    }

    @PreDestroy
    @Suppress("unused")
    fun destroy() {
        LOG.info("Destroying")
        unregisterVertxCodecs(vertx)
        verticles.forEach { id ->
            vertx.undeploy(id)
        }
    }

    fun getDataHandler(): DataHandler? {
        return qbusVerticle.dataHandler
    }

    private fun getOrThrow(prop: Properties, name: String): String {
        return prop.getProperty(name) ?: throw IllegalArgumentException("Can't find property for $name")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun registerVertxCodecs(vertx: Vertx) {
            LocalOnlyCodec.register(vertx, MqttItemWrapper::class.java)
            LocalOnlyCodec.register(vertx, StatusRequest::class.java)
            LocalOnlyCodec.register(vertx, MqttHandled::class.java)
            LocalOnlyCodec.register(vertx, MqttSensorItem::class.java)
        }

        fun unregisterVertxCodecs(vertx: Vertx) {
            LocalOnlyCodec.unregister(vertx, MqttItemWrapper::class.java)
            LocalOnlyCodec.unregister(vertx, StatusRequest::class.java)
            LocalOnlyCodec.unregister(vertx, MqttHandled::class.java)
            LocalOnlyCodec.unregister(vertx, MqttSensorItem::class.java)
        }
    }
}