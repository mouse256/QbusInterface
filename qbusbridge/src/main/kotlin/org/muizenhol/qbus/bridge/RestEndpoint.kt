package org.muizenhol.qbus.bridge

import org.muizenhol.qbus.bridge.type.MqttType
import org.muizenhol.qbus.bridge.type.StatusRequest
import org.muizenhol.qbus.sddata.SdDataStruct
import org.muizenhol.qbus.sddata.SdOutput
import org.muizenhol.qbus.sddata.SdOutputThermostat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import jakarta.enterprise.inject.Default
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.muizenhol.qbus.bridge.openhab.OpenHabFormatter


@Path("/qbus")
class ExampleResource {

    @Inject
    @field: Default
    lateinit var controller: ControllerHandler

    @GET
    @Path("update")
    fun updateAll(): String {
        controller.vertx.eventBus().send(QbusVerticle.ADDRESS_STATUS, StatusRequest.SEND_ALL_STATES)
        return "Update-all requested"
    }

    @GET
    @Path("raw")
    @Produces(MediaType.APPLICATION_JSON)
    fun raw(): SdDataStruct? {
        return controller.getDataHandler()?.data
    }

    @GET
    @Path("readerror")
    @Produces(MediaType.TEXT_PLAIN)
    fun readError() {
        LOG.info("Triggering read error")
        // return controller.controller.triggerReadError()
    }

    @GET
    @Path("openhab/things")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabThings(): String {
        val data = controller.getDataHandler()?.data ?: throw IllegalStateException("data not ready")
        val formatter = OpenHabFormatter(data, controller.mqttHost)

        return formatter.asThings()
    }

    @GET
    @Path("openhab/items")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabItems(): String {
        val data = controller.getDataHandler()?.data ?: throw IllegalStateException("data not ready")
        val formatter = OpenHabFormatter(data, controller.mqttHost)

        return formatter.asItems()
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}