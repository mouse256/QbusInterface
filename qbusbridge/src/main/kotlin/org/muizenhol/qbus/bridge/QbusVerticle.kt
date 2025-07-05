package org.muizenhol.qbus.bridge

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.muizenhol.qbus.sddata.SdOutput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.Callable

class QbusVerticle private constructor() : AbstractVerticle() {

    private lateinit var controller: Controller
    var dataHandler: DataHandler? = null
    lateinit var consumerStatus: MessageConsumer<StatusRequest>
    lateinit var consumer: MessageConsumer<MqttItemWrapper>

    constructor(
        username: String,
        password: String,
        serial: String,
        host: String
    ) : this() {
        controller = Controller(serial, username, password, host,
            { exception -> LOG.error("Exception", exception) },
            onReady = { dataHandler -> vertx.runOnContext { onControllerReady(dataHandler) } })
    }

    constructor(controller: Controller, onReady: Future<DataHandler>) : this() {
        this.controller = controller
        onReady.onSuccess(this::onControllerReady)
    }

    override fun start() {
        LOG.info("Starting")
        consumerStatus = vertx.eventBus().localConsumer(ADDRESS_STATUS, this::handleStatusRequest)
        consumer = vertx.eventBus().localConsumer(ADDRESS_UPDATE_QBUS_ITEM, this::handleQbusUpdateItem)
        controller.start()
    }

    override fun stop() {
        consumer.unregister()
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
            LOG.info("Publish current state 1")
            vertx.eventBus().publish(MqttVerticle.ADDRESS_INFO, dataHandler!!.data)
            LOG.info("Publish current state 2")
            vertx.executeBlocking(Callable {
                    dataHandler!!.data.outputs.values.forEach { out ->
                        Thread.sleep(20) //add a small delay to avoid overloading the mqtt bus
                        onDataUpdate(dataHandler!!.data.serialNumber, out)
                    }
                }
            )
        }
    }

    /**
     * Event from Qbus
     */
    private fun onDataUpdate(serial: String, data: SdOutput) {
        vertx.eventBus().publish(MqttVerticle.ADDRESS, MqttItemWrapper(serial, data))
    }

    /**
     * Incoming update request over mqtt.
     */
    private fun handleQbusUpdateItem(msg: Message<MqttItemWrapper>) {
        val item = msg.body()
        controller.requestNewQbusState(item.serial, item.data)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        val ADDRESS_STATUS = "ADDRESS_QBUS_VERTICLE_STATUS"
        val ADDRESS_UPDATE_QBUS_ITEM = "ADDRESS_UPDATE_QBUS_ITEM"
    }
}
