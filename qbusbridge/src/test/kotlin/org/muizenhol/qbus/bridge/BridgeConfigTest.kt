package org.muizenhol.qbus.bridge

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Properties

class BridgeConfigTest {

    private fun baseProps() = Properties().apply {
        setProperty("environment", "prod")
        setProperty("username", "user")
        setProperty("password", "pass")
        setProperty("serial", "123456")
        setProperty("host", "192.168.1.100")
        setProperty("mqtt.host", "127.0.0.1")
    }

    @Test
    fun `mqtt port defaults to 1883 when not set`() {
        val config = BridgeConfig.from(baseProps())
        assertThat(config.mqttPort, equalTo(1883))
    }

    @Test
    fun `mqtt port is parsed correctly when set as a string in properties file`() {
        val props = baseProps().apply { setProperty("mqtt.port", "1884") }
        val config = BridgeConfig.from(props)
        assertThat(config.mqttPort, equalTo(1884))
    }

    @Test
    fun `environment is parsed case-insensitively`() {
        val props = baseProps().apply { setProperty("environment", "dev") }
        val config = BridgeConfig.from(props)
        assertThat(config.environment, equalTo(Environment.DEV))
    }

    @Test
    fun `missing required property throws IllegalArgumentException`() {
        val props = baseProps().apply { remove("username") }
        assertThrows<IllegalArgumentException> { BridgeConfig.from(props) }
    }
}
