package org.muizenhol.qbus.bridge.openhab

import org.muizenhol.qbus.bridge.type.MqttType
import org.muizenhol.qbus.sddata.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class OpenHabFormatter(val data: SdDataStruct, val mqttHost: String) {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val TYPE_TRIGGER = "trigger"
        private val TYPE_SWITCH = "switch"
    }

    private fun ignoredOutputs(): Set<Int> {
        return data.outputs.values.flatMap { out ->
            if (out is SdOutputAudioGroup) {
                listOf(out.volumeDownId, out.volumeUpId, out.favoritesId, out.playPauseId)
            } else {
                emptyList()
            }
        }.toSet()
    }
    fun asThings(): String {
        val out = StringBuilder()
        out.append("Bridge mqtt:broker:qbusBroker [ host=\"${mqttHost}\", secure=false] {\n")

        val ignoredOutputs = ignoredOutputs()
        data.outputs.values
            .filterNot { ignoredOutputs.contains(it.id) }
            .map { output -> Pair(MqttType.fromQbus(output), output) }
            .sortedBy { output -> output.second.name }
            .forEach { outpair ->
                val output = outpair.second
                val mqttName = outpair.first.mqttName
                val type = if (output.readonly) "string" else outpair.first.getOpenhabItem().lowercase()
                LOG.debug("handling {}", output)
                when (output) {
                    is SdOutputAudio -> {
                        // nothing to be added here
                    }

                    else ->
                        out.append(
                            "  Thing topic ${
                                formatName("thing", output.name)
                            } \"${output.name}\" @ \"QBus\" {\n"
                        ).append("    Channels:\n")
                }


                val conf = outpair.first.getOpenhabChannelConfig()
                when (output) {
                    is SdOutputThermostat -> {
                        out.append(
                            formatChannel(
                                type,
                                output.name + "_measured",
                                data.serialNumber,
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
                                data.serialNumber,
                                mqttName,
                                output.id,
                                "set",
                                false,
                                conf
                            )
                        )
                    }

                    is SdOutputAudioGroup -> {
                        out.append(formatChannelInternal(
                            TYPE_SWITCH,
                            "play",
                            data.serialNumber,
                            mqttName,
                            output.playPauseId,
                            "state",
                            output.readonly,
                            conf
                        ))
                        out.append(formatChannelInternal(
                            TYPE_TRIGGER,
                            "vol_up",
                            data.serialNumber,
                            "event",
                            output.volumeUpId,
                            "event",
                            true,
                            ""
                        ))
                        out.append(formatChannelInternal(
                            TYPE_TRIGGER,
                            "vol_down",
                            data.serialNumber,
                            "event",
                            output.volumeDownId,
                            "event",
                            true,
                            ""
                        ))
                    }

                    else -> out.append(
                        formatChannel(
                            type,
                            output.name,
                            data.serialNumber,
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

        out.append("}\n")
        return out.toString()
    }

    fun asItems(): String {
        val out = StringBuilder()

        val ignoredOutputs = ignoredOutputs()
        data.outputs.values
            .filterNot { ignoredOutputs.contains(it.id) }
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
                    is SdOutputAudioGroup ->
                        out.append(
                            formatItem(
                                outPair.first.getOpenhabItem(),
                                output.name,
                                output.name,
                                output.name,
                                "play"
                            )
                        )

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

        return out.toString()
    }

    private fun formatName(type: String, output: SdOutput): String {
        return "qbus_${type}_" + output.name.replace(" ", "_")
    }

    private fun formatName(type: String, name: String): String {
        return "qbus_${type}_" + name.replace(" ", "_")
    }

    private fun formatChannel(
        type: String,
        internalName: String,
        serial: String,
        mqttName: String,
        id: Int,
        stateName: String,
        readOnly: Boolean,
        conf: String
    ): String {
        return formatChannelInternal(type, formatName("channel", internalName),
            serial, mqttName, id, stateName, readOnly, conf)
    }

    private fun formatChannelInternal(
        type: String,
        name: String,
        serial: String,
        mqttName: String,
        id: Int,
        stateName: String,
        readOnly: Boolean,
        conf: String
    ): String {
        val p1 = "Type ${type}: ${name} ["
        val state = "stateTopic=\"qbus/${serial}/sensor/${mqttName}/${id}/${stateName}\""
        val command = if (readOnly) "" else ", commandTopic=\"qbus/${serial}/sensor/${mqttName}/${id}/command\""
        var out = "      ${p1}${state}${command}"
        if (conf.isNotEmpty()) {
            out += ", ${conf}"
        }
        out += "]\n"
        return out
    }

    /**
     * @param type OpenHab type
     * @param internalName Internal name
     * @param name Human readable name
     */
    fun formatItem(type: String, internalName: String, outputName: String, name: String): String {
        return formatItem(type, internalName, outputName, name, formatName("channel", internalName))
    }

    fun formatItem(type: String, internalName: String, outputName: String, name: String, channelName: String): String {
        val p1 = "${type} ${formatName("item", internalName)} \"${name}\""
        val p2 = "{channel=\"mqtt:topic:qbusBroker:${formatName("thing", outputName)}:" +
                "${channelName}\"}"
        return "${p1} ${p2}\n"
    }
}