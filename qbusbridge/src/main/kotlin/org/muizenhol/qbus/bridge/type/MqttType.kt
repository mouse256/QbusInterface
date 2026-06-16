package org.muizenhol.qbus.bridge.type

import org.muizenhol.qbus.sddata.*


enum class MqttType(val mqttName: String) {
    ON_OFF("switch"),
    DIMMER("dimmer"),
    THERMOSTAT("thermostat"),
    SHUTTER("shutter"),
    EVENT("event");


    fun getOpenhabChannelConfig(): String {
        return when (this) {
            ON_OFF -> "off=\"0\", on=\"255\""
            DIMMER -> "min=\"0\", max=\"100\", transformationPattern=\"JS:255to100.js\", transformationPatternOut=\"JS:100to255.js\""
            THERMOSTAT -> "min=\"0\", max=\"30\", step=\"0.5\", unit=\"°C\""
            SHUTTER -> "up=\"${SHUTTER_PAYLOAD_OPEN}\", down=\"${SHUTTER_PAYLOAD_CLOSE}\", stop=\"${SHUTTER_PAYLOAD_STOP}\""
            EVENT -> ""
        }
    }

    fun getOpenhabItem(): String {
        return when (this) {
            ON_OFF -> "Switch"
            DIMMER -> "Dimmer"
            THERMOSTAT -> "Number"
            SHUTTER -> "Rollershutter"
            EVENT -> "Event"
        }
    }

    companion object {
        // Command payloads shared between the HomeAssistant cover and the OpenHab rollershutter.
        const val SHUTTER_PAYLOAD_OPEN = "OPEN"
        const val SHUTTER_PAYLOAD_CLOSE = "CLOSE"
        const val SHUTTER_PAYLOAD_STOP = "STOP"

        // State payloads published on the shutter state topic.
        const val SHUTTER_STATE_OPENING = "opening"
        const val SHUTTER_STATE_CLOSING = "closing"
        const val SHUTTER_STATE_STOPPED = "stopped"

        fun fromQbus(item: SdOutput): MqttType {
            return when (item) {
                is SdOutputOnOff -> ON_OFF
                is SdOutputTimer -> ON_OFF
                is SdOutputTimer2 -> ON_OFF
                is SdOutputDimmer -> DIMMER
                is SdOutputThermostat -> THERMOSTAT
                is SdOutputShutter -> SHUTTER
                is SdOutputAudio -> EVENT
                is SdOutputAudioGroup -> ON_OFF
            }
        }

        fun fromMqtt(type: String): MqttType? {
            return entries
                .iterator()
                .asSequence()
                .find { x -> x.mqttName == type }
        }
    }
}
