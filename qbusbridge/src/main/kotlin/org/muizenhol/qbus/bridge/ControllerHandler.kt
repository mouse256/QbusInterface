package org.muizenhol.qbus.bridge

import io.quarkus.runtime.Startup
import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import org.muizenhol.qbus.DataHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.regex.Pattern
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
    fun create() {
        LOG.info("creating vertx")
        vertx = Vertx.vertx()

        val prop = Properties()
        val file = File(System.getenv("QBUS_PROPERTY_FILE") ?: "/tmp/qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val username = getOrThrow(prop, "username")
        val password = getOrThrow(prop, "password")
        val serial = getOrThrow(prop, "serial")
        val host = getOrThrow(prop, "host")

        mqttHost = getOrThrow(prop, "mqtt.host")
        val mqttPort = prop.getOrDefault("mqtt.port", 1883) as Int

        val mqttVerticle = MqttVerticle(mqttHost, mqttPort)
        vertx.deployVerticle(mqttVerticle)

        qbusVerticle = QbusVerticle(username, password, serial, host)
        vertx.deployVerticle(qbusVerticle, this::verticleDeployed)
    }

    fun verticleDeployed(id: AsyncResult<String>) {
        if (id.failed()) {
            LOG.error("Verticle deploy failed")
        } else {
            verticles.add(id.result())
        }
    }

    @PreDestroy
    fun destroy() {
        LOG.info("Destroying")
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
        private val PATTERN_COMMAND = Pattern.compile("qbus/([^/]+)/([^/]+)/(\\d+)/command")
    }
}