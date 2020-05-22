package org.muizenhol.qbus.bridge

import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import javax.enterprise.inject.Default
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType


@Path("/qbus")
class ExampleResource {

    @Inject
    @field: Default
    lateinit var controller: ControllerHandler

    @GET
    @Path("hello")
    //@Produces("application/rss+xml;charset=UTF-8")
    fun hello(): String {
        return "hello: ${controller.dataHandler}\n"
    }

    @GET
    @Path("raw")
    @Produces(MediaType.APPLICATION_JSON)
    fun raw(): SdDataStruct? {
        return controller.dataHandler?.data
    }

    @GET
    @Path("openhab/things")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabThings(): String {
        val out = StringBuilder()
        out.append("Bridge mqtt:broker:qbusBroker [ host=\"${controller.mqttHost}\", secure=false] {\n")
        val data = controller.dataHandler?.data
        data?.let {
            it.outputs.values
                .filter { output -> output.type == SdDataStruct.Type.ON_OFF }
                .forEach { output ->
                    out.append("  Thing topic qbus_thing_${output.id} \"${output.name}\" @ \"QBus\" {\n")
                        .append("    Channels:\n")
                        .append("      Type switch : qbus_channel_${output.id} ")
                        .append("[ stateTopic=\"qbus/${it.serialNumber}/switch/${output.id}/state\" , ")
                        .append("commandTopic=\"qbus/${it.serialNumber}/switch/${output.id}/command\", ")
                        .append("on=\"ON\", off=\"OFF\"]\n")
                        .append("  }\n")
                }
        }
        out.append("}\n")
        return out.toString()
    }

    @GET
    @Path("openhab/items")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabItems(): String {
        val out = StringBuilder()
        val data = controller.dataHandler?.data
        data?.let {
            it.outputs.values
                .filter { output -> output.type == SdDataStruct.Type.ON_OFF }
                .forEach { output ->
                    out.append("Switch qbus_item_${output.id} \"${output.name}\" ")
                        .append("{channel=\"mqtt:topic:qbusBroker:qbus_thing_${output.id}:qbus_channel_${output.id}\"}\n")

                }
        }
        return out.toString()
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}