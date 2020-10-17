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
    lateinit var consumer: MessageConsumer<MqttItem>

    init {
        controller = Controller(serial, username, password, host,
            { exception -> LOG.error("Exception", exception) },
            onReady = { dataHandler -> vertx.runOnContext { onControllerReady(dataHandler) } })
    }

    override fun start() {
        LOG.info("Starting")
        LocalOnlyCodec.register(vertx, StatusRequest::class.java)
        consumerStatus = vertx.eventBus().localConsumer(ADDRESS_STATUS, this::handleStatusRequest)
        consumer = vertx.eventBus().localConsumer(ADDRESS_UPDATE_QBUS_ITEM, this::handleQbusUpdateItem)
        controller.start()
    }

    override fun stop() {
        LocalOnlyCodec.unregister(vertx, StatusRequest::class.java)
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
    private fun onDataUpdate(serial: String, data: SdDataStruct.Output) {
        try {
            data.value?.let { payload ->
                LOG.info("update for {}({}) to {}", data.name, serial, payload)
                val type = Type.fromQbusInternal(data.type)
                type?.run {
                    vertx.eventBus().send(MqttVerticle.ADDRESS, MqttItem(serial, type, data.id, payload))
                }
            }
        } catch (ex: Exception) {
            LOG.warn("Error processing qbus data", ex)
        }
    }

    private fun handleQbusUpdateItem(msg: Message<MqttItem>) {
        val item = msg.body()
        if (dataHandler == null) {
            LOG.debug("DataHandler is not yet initalized")
            return
        }
        if (dataHandler!!.data.serialNumber != item.serial) {
            LOG.debug("Got msg for another controller")
            return
        }
        val out = dataHandler?.getOutput(item.id)
        if (out != null) {
            out.value = item.payload
            controller.setNewState(out)
        } else {
            LOG.warn("can't find output with id {}", item.payload)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        val ADDRESS_STATUS = "ADDRESS_QBUS_VERTICLE_STATUS"
        val ADDRESS_UPDATE_QBUS_ITEM = "ADDRESS_UPDATE_QBUS_ITEM"
    }

    enum class StatusRequest {
        SEND_ALL_STATES
    }

    data class MqttItem(val serial: String, val type: Type, val id: Int, val payload: Byte)

    enum class Type {
        ON_OFF;

        companion object {
            fun fromQbusInternal(intType: SdDataStruct.Type): Type? {
                return when (intType) {
                    SdDataStruct.Type.ON_OFF -> ON_OFF
                    else -> null
                }
            }
        }
    }

}