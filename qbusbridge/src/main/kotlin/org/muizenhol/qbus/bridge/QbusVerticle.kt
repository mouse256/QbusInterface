package org.muizenhol.qbus.bridge

import io.vertx.core.AbstractVerticle
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class QbusVerticle(
    username: String,
    password: String,
    serial: String,
    host: String
) : AbstractVerticle() {

    private val controller: Controller
    var dataHandler: DataHandler? = null
    lateinit var consumerStatus: MessageConsumer<StatusRequest>
    lateinit var consumer: MessageConsumer<MqttVerticle.MqttReceiveItem>

    init {
        controller = Controller(serial, username, password, host,
            { exception -> LOG.error("Exception", exception) },
            onReady = { dataHandler -> vertx.runOnContext { onControllerReady(dataHandler) } })
    }

    override fun start() {
        LOG.info("Starting")
        LocalOnlyCodec.register(vertx, StatusRequest::class.java)
        consumerStatus = vertx.eventBus().localConsumer(ADDRESS_STATUS, this::handleStatusRequest)
        consumer = vertx.eventBus().localConsumer(ADDRESS, this::handleRequest)
        controller.start()
    }

    override fun stop() {
        LocalOnlyCodec.unregister(vertx, StatusRequest::class.java)
        controller.close()
    }

    private fun handleStatusRequest(msg: Message<StatusRequest>) {
        LOG.info("Handle Qbus status request: {}", msg)
        when (msg.body()) {
            null -> LOG.error("Got null statusRequest")
            StatusRequest.SEND_ALL_STATES -> sendAllStates()
        }
    }

    private fun onControllerReady(dataHandler: DataHandler) {
        LOG.info("Qbus Ready")
        this.dataHandler = dataHandler
        dataHandler.setEventListener(this::onDataUpdate)
        sendAllStates()
    }

    private fun sendAllStates() {
        //publish current state
        if (dataHandler == null) {
            LOG.debug("Qbus not yet initialized, ignoring request")
        } else {
            LOG.info("Publish current state")
            vertx.executeBlocking(Handler<Promise<String>> {
                dataHandler!!.data.outputs.values.forEach { out ->
                    Thread.sleep(20) //add a small delay to avoid overloading the mqtt bus
                    onDataUpdate(dataHandler!!.data.serialNumber, out)
                }
                it.complete()
            }, Handler { /*nothing*/ })
        }
    }

    /**
     * Event from Qbus
     */
    private fun onDataUpdate(serial: String, data: SdDataStruct.Output) {
        try {
            LOG.info("update for {}({}) to {}", data.name, serial, data.value)
            vertx.eventBus().send(MqttVerticle.ADDRESS, MqttVerticle.MqttItem(serial, data))
        } catch (ex: Exception) {
            LOG.warn("Error processing qbus data", ex)
        }
    }

    private fun handleRequest(msg: Message<MqttVerticle.MqttReceiveItem>) {
        LOG.info("Handle update from mqtt request: {}", msg)
        if (dataHandler == null) {
            LOG.debug("DataHandler is not yet initalized")
        } else {
            msg.body().let { data ->
                if (dataHandler!!.data.serialNumber != data.serial) {
                    LOG.debug("Got msg for another controller")
                    return
                }
                when (data.type) {
                    "switch" -> handleSwitchUpdate(data.id, data.payload)
                    else -> LOG.warn("Can't handle type {}", data.type)
                }
            }
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


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        val ADDRESS_STATUS = "ADDRESS_QBUS_VERTICLE_STATUS"
        val ADDRESS = "ADDRESS_QBUS_VERTICLE"
    }

    enum class StatusRequest {
        SEND_ALL_STATES
    }
}