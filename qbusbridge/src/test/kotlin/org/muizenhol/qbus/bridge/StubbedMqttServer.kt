package org.muizenhol.qbus.bridge

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.junit5.VertxTestContext
import io.vertx.mqtt.MqttEndpoint
import io.vertx.mqtt.MqttServer
import io.vertx.mqtt.MqttServerOptions
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class StubbedMqttServer {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
    var mqttServer: MqttServer? = null
val clients = mutableListOf<MqttEndpoint>()

    fun setupMqttServer(vertx: Vertx, vertxContext: VertxTestContext, ready: (Int) -> Unit) {
        val checkpoint = vertxContext.checkpoint()
        val opts = MqttServerOptions().setPort(0)
        mqttServer = MqttServer.create(vertx, opts)
        mqttServer?.endpointHandler { client ->
            // shows main connect info
            LOG.info("MQTT client [{}}] request to connect", client.clientIdentifier())

            clients.add(client)
            // accept connection from the remote client
            client.accept()

            client.publishHandler { msg ->
                LOG.info("SERVER: got msg on {}", msg.topicName())
                clients.forEach{ c ->
                    //TODO: publish to correct clients
                    c.publish(msg.topicName(), msg.payload(), msg.qosLevel(), false, false)
                }
            }
        }?.listen { ar ->
            if (ar.succeeded()) {
                val port = ar.result().actualPort()
                LOG.info("MQTT server is listening on port {}", port)
                ready(port)
                checkpoint.flag()
            } else {
                LOG.error("Error on starting the server")
                vertxContext.failNow(ar.cause())
            }
        }
        mqttServer!!.exceptionHandler{t -> vertxContext.failNow(t)}
    }

    fun close(completionHandler: Handler<AsyncResult<Void>>) {
        mqttServer?.close(completionHandler)
    }
}