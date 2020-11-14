package org.muizenhol.qbus.bridge

import io.vertx.core.AbstractVerticle
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.bridge.type.MqttHandled
import org.muizenhol.qbus.bridge.type.MqttItem
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.muizenhol.qbus.sddata.SdOutput
import org.muizenhol.qbus.sddata.SdOutputDimmer
import org.muizenhol.qbus.sddata.SdOutputOnOff
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
    lateinit var consumer: MessageConsumer<MqttItem>

    init {
        controller = Controller(serial, username, password, host,
            { exception -> LOG.error("Exception", exception) },
            onReady = { dataHandler -> vertx.runOnContext { onControllerReady(dataHandler) } })
    }

    override fun start() {
        LOG.info("Starting")
        LocalOnlyCodec.register(vertx, StatusRequest::class.java)
        LocalOnlyCodec.register(vertx, MqttHandled::class.java)
        consumerStatus = vertx.eventBus().localConsumer(ADDRESS_STATUS, this::handleStatusRequest)
        consumer = vertx.eventBus().localConsumer(ADDRESS_UPDATE_QBUS_ITEM, this::handleQbusUpdateItem)
        controller.start()
    }

    override fun stop() {
        LocalOnlyCodec.unregister(vertx, StatusRequest::class.java)
        LocalOnlyCodec.unregister(vertx, MqttHandled::class.java)
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
    private fun onDataUpdate(serial: String, data: SdOutput) {
        try {
            when (data) {
                is SdOutputOnOff -> onDataUpdateSingleValue(serial, data, MqttItem.Type.ON_OFF, data.value)
                is SdOutputDimmer -> onDataUpdateSingleValue(serial, data, MqttItem.Type.DIMMER, data.value)
            }
        } catch (ex: Exception) {
            LOG.warn("Error processing qbus data", ex)
        }
    }

    private fun onDataUpdateSingleValue(serial: String, data: SdOutput, type: MqttItem.Type, payload: Byte) {
        val payloadInt = (payload.toInt() and 0xff)
        LOG.info("update for {}({}) to {}", data.name, serial, payloadInt)
        vertx.eventBus().send(
            MqttVerticle.ADDRESS,
            MqttItem(serial, type, data.id, payloadInt, data.name, data.place.name)
        )
    }

    private fun handleQbusUpdateItem(msg: Message<MqttItem>) {
        val item = msg.body()
        if (dataHandler == null) {
            LOG.debug("DataHandler is not yet initalized")
            return
        }
        LOG.debug("Update for ({}) {} -> {}", item.type, item.id, item.payload)
        if (dataHandler!!.data.serialNumber != item.serial) {
            LOG.debug("Got msg for another controller")
            return
        }
        val out = dataHandler?.getOutput(item.id)
        if (out != null) {
            when (out) {
                is SdOutputOnOff -> {
                    out.value = item.payload.toByte()
                    true
                }
                is SdOutputDimmer -> {
                    out.value = item.payload.toByte()
                    true
                }
                else -> false
            }.takeIf { x -> x }.run {
                controller.requestNewQbusState(out)
            }
        } else {
            LOG.warn("can't find output with id {}", item.payload)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        val ADDRESS_STATUS = "ADDRESS_QBUS_VERTICLE_STATUS"
        val ADDRESS_UPDATE_QBUS_ITEM = "ADDRESS_UPDATE_QBUS_ITEM"
    }
}
