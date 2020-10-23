package org.muizenhol.qbus.bridge

import org.muizenhol.qbus.bridge.type.MqttItem
import org.muizenhol.qbus.bridge.type.StatusRequest
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
        val out = StringBuilder()
        out.append("Bridge mqtt:broker:qbusBroker [ host=\"${controller.mqttHost}\", secure=false] {\n")
        val data = controller.getDataHandler()?.data
        data?.let {
            it.outputs.values
                .map { output-> Pair(MqttItem.Type.fromQbusInternal(output.type), output) }
                .filter { out -> out.first != null }
                .sortedBy { output -> output.second.name }
                .forEach { outpair ->
                    val output = outpair.second
                    val type = getOpenhabType(output).toLowerCase()
                    out.append("  Thing topic ${formatName("thing", output)} \"${output.name}\" @ \"QBus\" {\n")
                        .append("    Channels:\n")
                        .append("      Type ").append(type)
                        .append(" : ${formatName("channel", output)} ")
                        .append("[ stateTopic=\"qbus/${it.serialNumber}/${type}/${output.id}/state\" ")
                    if (!output.readonly) {
                        out.append(", commandTopic=\"qbus/${it.serialNumber}/${type}/${output.id}/command\"")
                    }
                    out.append(", ").append(outpair.first!!.getOpenhabThing()).append("]\n")
                        .append("  }\n")
                }
        }
        out.append("}\n")
        return out.toString()
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
                .map { output-> Pair(MqttItem.Type.fromQbusInternal(output.type), output) }
                .filter { out -> out.first != null }
                .sortedBy { output -> output.second.name }
                .forEach { outPair ->
                    val output = outPair.second
                    out.append("${outPair.first!!.getOpenhabItem()} ${formatName("item", output)} \"${output.name}\" ")
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