package org.muizenhol.qbus.bridge.type

import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

data class MqttItem(val serial: String, val type: Type, val id: Int, val payload: Int) {

    enum class Type {
        ON_OFF,
        DIMMER;

        fun getOpenhabThing(): String {
            return when (this) {
                ON_OFF -> "off=\"0\", on=\"255\""
                DIMMER -> "min=\"0\", max=\"100\", transformationPattern=\"JS:255to100.js\", transformationPatternOut=\"JS:100to255.js\""
            }
        }

        fun getOpenhabItem(): String {
            return when (this) {
                ON_OFF -> "Switch"
                DIMMER -> "Dimmer"
            }
        }

        fun toQbusInternal(payload: String): Int? {
            return when (this) {
                ON_OFF -> return when (payload) {
                    "0" -> 0
                    "255" -> 255
                    else -> {
                        LOG.warn("Invalid ON_OFF payload: {}", payload)
                        null
                    }
                }
                DIMMER -> {
                    val p = payload.toDoubleOrNull()
                        ?.toInt()
                        ?.takeIf { i -> i in 0..255 }
                    if (p != null) {
                        return p
                    }
                    else {
                        LOG.warn("Invalid dimmer payload: {}", payload)
                        null
                    }
                }
            }
        }

        companion object {
            private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

            fun fromQbusInternal(intType: SdDataStruct.Type): Type? {
                return when (intType) {
                    SdDataStruct.Type.ON_OFF -> ON_OFF
                    SdDataStruct.Type.DIMMER1B, SdDataStruct.Type.DIMMER2B -> DIMMER
                    else -> {
                        LOG.warn("Can't map type from qbus: {}", intType)
                        null
                    }
                }
            }

            fun fromMqtt(type: String): Type? {
                return when (type.toLowerCase()) {
                    "switch" -> ON_OFF
                    "dimmer" -> DIMMER
                    else -> {
                        LOG.warn("Can't map type from mqtt: {}", type)
                        null
                    }
                }
            }
        }
    }
}