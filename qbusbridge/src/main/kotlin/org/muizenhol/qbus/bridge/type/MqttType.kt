package org.muizenhol.qbus.bridge.type

import org.muizenhol.qbus.sddata.SdOutput
import org.muizenhol.qbus.sddata.SdOutputDimmer
import org.muizenhol.qbus.sddata.SdOutputOnOff
import org.muizenhol.qbus.sddata.SdOutputThermostat


enum class MqttType(val mqttName: String) {
    ON_OFF("switch"),
    DIMMER("dimmer"),
    THERMOSTAT("thermostat");

    fun getOpenhabChannelConfig(): String {
        return when (this) {
            ON_OFF -> "off=\"0\", on=\"255\""
            DIMMER -> "min=\"0\", max=\"100\", transformationPattern=\"JS:255to100.js\", transformationPatternOut=\"JS:100to255.js\""
            THERMOSTAT -> "min=\"0\", max=\"30\", step=\"0.5\", unit=\"°C\""
        }
    }

    fun getOpenhabItem(): String {
        return when (this) {
            ON_OFF -> "Switch"
            DIMMER -> "Dimmer"
            THERMOSTAT -> "Number"
        }
    }

    companion object {
        fun fromQbus(item: SdOutput): MqttType {
            return when (item) {
                is SdOutputOnOff -> ON_OFF
                is SdOutputDimmer -> DIMMER
                is SdOutputThermostat -> THERMOSTAT
            }
        }

        fun fromMqtt(type: String): MqttType? {
            return values()
                .iterator()
                .asSequence()
                .find { x -> x.mqttName == type }
        }
    }
}
