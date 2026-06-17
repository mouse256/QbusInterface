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
            SHUTTER -> ""
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
        // Full-open / full-close command payloads understood on the shutter command topic.
        // (HomeAssistant cover sends OPEN/CLOSE; OpenHab rollershutter sends UP/DOWN. A bare
        // number 0-100 sets an exact position. The ROL02P protocol has no stop command.)
        const val SHUTTER_PAYLOAD_OPEN = "OPEN"
        const val SHUTTER_PAYLOAD_CLOSE = "CLOSE"
        const val SHUTTER_POSITION_OPEN = 100
        const val SHUTTER_POSITION_CLOSED = 0

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
