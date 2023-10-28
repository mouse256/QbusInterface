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
                .map { output -> Pair(MqttType.fromQbus(output), output) }
                .sortedBy { output -> output.second.name }
                .forEach { outpair ->
                    val output = outpair.second
                    val mqttName = outpair.first.mqttName
                    val type = if (output.readonly) "string" else outpair.first.getOpenhabItem().lowercase()
                    out.append("  Thing topic ${formatName("thing", output.name)} \"${output.name}\" @ \"QBus\" {\n")
                        .append("    Channels:\n")

                    val conf = outpair.first.getOpenhabChannelConfig()
                    when (output) {
                        is SdOutputThermostat -> {
                            out.append(
                                formatChannel(
                                    type,
                                    output.name + "_measured",
                                    it.serialNumber,
                                    mqttName,
                                    output.id,
                                    "measured",
                                    true,
                                    conf
                                )
                            )
                            out.append(
                                formatChannel(
                                    type,
                                    output.name + "_set",
                                    it.serialNumber,
                                    mqttName,
                                    output.id,
                                    "set",
                                    false,
                                    conf
                                )
                            )
                        }
                        else -> out.append(
                            formatChannel(
                                type,
                                output.name,
                                it.serialNumber,
                                mqttName,
                                output.id,
                                "state",
                                output.readonly,
                                conf
                            )
                        )
                    }
                    out.append("  }\n")
                }
        }
        out.append("}\n")
        return out.toString()
    }

    private fun formatName(type: String, output: SdOutput): String {
        return "qbus_${type}_" + output.name.replace(" ", "_")
    }

    private fun formatName(type: String, name: String): String {
        return "qbus_${type}_" + name.replace(" ", "_")
    }

    fun formatChannel(
        type: String,
        internalName: String,
        serial: String,
        mqttName: String,
        id: Int,
        stateName: String,
        readOnly: Boolean,
        conf: String
    ): String {
        val p1 = "Type ${type}: ${formatName("channel", internalName)} ["
        val state = "stateTopic=\"qbus/${serial}/sensor/${mqttName}/${id}/${stateName}\""
        val command = if (readOnly) "" else ", commandTopic=\"qbus/${serial}/sensor/${mqttName}/${id}/command\""
        return "      ${p1}${state}${command}, ${conf}]\n"
    }

    /**
     * @param type OpenHab type
     * @param internalName Internal name
     * @param name Human readable name
     */
    fun formatItem(type: String, internalName: String, outputName: String, name: String): String {
        val p1 = "${type} ${formatName("item", internalName)} \"${name}\""
        val p2 = "{channel=\"mqtt:topic:qbusBroker:${formatName("thing", outputName)}:" +
                "${formatName("channel", internalName)}\"}"
        return "${p1} ${p2}\n"
    }

    @GET
    @Path("openhab/items")
    @Produces(MediaType.TEXT_PLAIN)
    fun openhabItems(): String {
        val out = StringBuilder()
        val data = controller.getDataHandler()?.data
        data?.let {
            it.outputs.values
                .map { output -> Pair(MqttType.fromQbus(output), output) }
                .sortedBy { output -> output.second.name }
                .forEach { outPair ->
                    val output = outPair.second
                    when (output) {
                        is SdOutputThermostat -> {
                            out.append(
                                formatItem(
                                    outPair.first.getOpenhabItem(),
                                    output.name + "_measured",
                                    output.name,
                                    "Measured"
                                )
                            )
                            out.append(
                                formatItem(
                                    outPair.first.getOpenhabItem(),
                                    output.name + "_set",
                                    output.name,
                                    "Set"
                                )
                            )
                        }
                        else ->
                            out.append(
                                formatItem(
                                    outPair.first.getOpenhabItem(),
                                    output.name,
                                    output.name,
                                    output.name
                                )
                            )
                    }
                }
        }
        return out.toString()
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}