package org.muizenhol.qbus.bridge

import java.util.Properties

data class BridgeConfig(
    val environment: Environment,
    val username: String,
    val password: String,
    val serial: String,
    val host: String,
    val mqttHost: String,
    val mqttPort: Int
) {
    companion object {
        fun from(prop: Properties): BridgeConfig = BridgeConfig(
            environment = Environment.valueOf(required(prop, "environment").uppercase()),
            username = required(prop, "username"),
            password = required(prop, "password"),
            serial = required(prop, "serial"),
            host = required(prop, "host"),
            mqttHost = required(prop, "mqtt.host"),
            // Properties values are always Strings; getOrDefault("mqtt.port", 1883) as Int throws ClassCastException
            mqttPort = prop.getProperty("mqtt.port")?.toInt() ?: 1883
        )

        private fun required(prop: Properties, name: String): String =
            prop.getProperty(name) ?: throw IllegalArgumentException("Missing required property: $name")
    }
}
