package org.muizenhol.qbus.bridge

import io.quarkus.runtime.Startup
import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.bridge.type.MqttHandled
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Default
import jakarta.inject.Inject
import java.io.File
import java.io.FileReader
import java.lang.invoke.MethodHandles
import java.util.Properties

@Startup
@ApplicationScoped
class ControllerHandler {
    lateinit var mqttHost: String

    @Inject
    @field:Default
    lateinit var vertx: Vertx

    lateinit var qbusVerticle: QbusVerticle
    val verticles = mutableListOf<String>()


    @PostConstruct
    @Suppress("unused")
    fun create() {
        LOG.info("creating verticles")
        val prop = Properties()
        val file = File(System.getenv("QBUS_PROPERTY_FILE") ?: "/tmp/qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val config = BridgeConfig.from(prop)
        val environment = config.environment
        val username = config.username
        val password = config.password
        val serial = config.serial
        val host = config.host

        mqttHost = config.mqttHost
        val mqttPort = config.mqttPort

        registerVertxCodecs(vertx)

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

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun registerVertxCodecs(vertx: Vertx) {
            LocalOnlyCodec.register(vertx, MqttItemWrapper::class.java)
            LocalOnlyCodec.register(vertx, StatusRequest::class.java)
            LocalOnlyCodec.register(vertx, MqttHandled::class.java)
            LocalOnlyCodec.register(vertx, SdDataStruct::class.java)
        }

        fun unregisterVertxCodecs(vertx: Vertx) {
            LocalOnlyCodec.unregister(vertx, MqttItemWrapper::class.java)
            LocalOnlyCodec.unregister(vertx, StatusRequest::class.java)
            LocalOnlyCodec.unregister(vertx, MqttHandled::class.java)
            LocalOnlyCodec.unregister(vertx, SdDataStruct::class.java)
        }
    }
}