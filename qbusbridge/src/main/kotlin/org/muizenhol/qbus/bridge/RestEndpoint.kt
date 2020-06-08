package org.muizenhol.qbus.bridge

import io.vertx.core.Vertx
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
    @Path("update")
    fun updateAll(): String {
        controller.vertx.eventBus().send(QbusVerticle.ADDRESS_STATUS, QbusVerticle.StatusRequest.SEND_ALL_STATES)
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
    fun readError(): Unit {
        LOG.info("Triggering read error")
        // return controller.controller.triggerReadError()
    }

    @GET
    @Path("openhab/things")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabThings(): String {
        val out = StringBuilder()
        out.append("Bridge mqtt:broker:qbusBroker [ host=\"${controller.mqttHost}\", secure=false] {\n")
        val data = controller.getDataHandler()?.data
        data?.let {
            it.outputs.values
                .filter { output ->
                    output.type == SdDataStruct.Type.ON_OFF
                            || output.type == SdDataStruct.Type.DIMMER2B
                            || output.type == SdDataStruct.Type.DIMMER1B
                }
                .sortedBy { output -> output.name }
                .forEach { output ->
                    out.append("  Thing topic ${formatName("thing", output)} \"${output.name}\" @ \"QBus\" {\n")
                        .append("    Channels:\n")
                        .append("      Type ").append(getOpenhabType(output))
                        .append(" : ${formatName("channel", output)} ")
                        .append("[ stateTopic=\"qbus/${it.serialNumber}/switch/${output.id}/state\" ")
                    if (!output.readonly) {
                        out.append(", commandTopic=\"qbus/${it.serialNumber}/switch/${output.id}/command\"")
                    }
                    out.append(", ").append(getOpenhabStates(output)).append("\n")
                        .append("  }\n")
                }
        }
        out.append("}\n")
        return out.toString()
    }

    private fun getOpenhabStates(output: SdDataStruct.Output): String {
        return when (output.type) {
            SdDataStruct.Type.ON_OFF -> "on=\"ON\", off=\"OFF\""
            SdDataStruct.Type.DIMMER1B, SdDataStruct.Type.DIMMER2B -> "min=\"0\", max=\"100\", step=\"0.5\""
            else -> throw IllegalStateException("Not handled")
        }
    }

    private fun getOpenhabType(output: SdDataStruct.Output): String {
        if (output.readonly) {
            return "string"
        } else {
            return when (output.type) {
                SdDataStruct.Type.ON_OFF -> "Switch"
                SdDataStruct.Type.DIMMER1B, SdDataStruct.Type.DIMMER2B -> "Dimmer"
                else -> throw IllegalStateException("Not handled")
            }
        }
    }

    private fun formatName(type: String, output: SdDataStruct.Output): String {
        return "qbus_${type}_" + output.name.replace(" ", "_")
    }

    @GET
    @Path("openhab/items")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabItems(): String {
        val out = StringBuilder()
        val data = controller.getDataHandler()?.data
        data?.let {
            it.outputs.values
                .filter { output -> output.type == SdDataStruct.Type.ON_OFF }
                .sortedBy { output -> output.name }
                .forEach { output ->
                    out.append("Switch ${formatName("item", output)} \"${output.name}\" ")
                        .append(
                            "{channel=\"mqtt:topic:qbusBroker:${formatName("thing", output)}:${formatName(
                                "channel",
                                output
                            )}\"}\n"
                        )

                }
        }
        return out.toString()
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}