package org.muizenhol.qbus.bridge.homeassistant

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.mqtt.messages.MqttMessage
import io.vertx.mqtt.messages.MqttPublishMessage
import org.muizenhol.qbus.bridge.homeassistant.Discovery.Component.DeviceClass
import org.muizenhol.qbus.bridge.homeassistant.Discovery.Component.Platform
import java.util.*

data class Discovery(
    @field:JsonProperty("device") @param:JsonProperty(
        "device"
    ) val device: Device?,

    @field:JsonProperty("origin") @param:JsonProperty(
        "origin"
    ) val origin: Origin?,

    @field:JsonProperty("state_topic") @param:JsonProperty(
        "state_topic"
    ) val stateTopic: String?,

    @field:JsonProperty("components") @param:JsonProperty(
        "components"
    ) val components: Map<String, Component2>
) {
    data class Device(
        val identifiers: String?,
        val manufacturer: String?,
        val model: String?,
        val name: String?
    )

    @JvmRecord
    data class Origin(val name: String?)


    open class Base(p: Int)

    class Derived(p: Int) : Base(p)

    data class Switch(
        override val name: String,
        override val uniqueId: String,

        @field:JsonProperty("command_topic")
        @param:JsonProperty("command_topic")
        val commandTopic: String,

        @field:JsonProperty("state_topic")
        @param:JsonProperty("state_topic")
        val stateTopic: String,

        @field:JsonProperty("payload_on")
        @param:JsonProperty("payload_on")
        val payloadOn: String? = null,

        @field:JsonProperty("payload_off")
        @param:JsonProperty("payload_off")
        val payloadOff: String? = null,
    ) : Component2(
        name,
        uniqueId,
        Component.Platform.SWITCH,
    )

    open class Component2(
        @field:JsonProperty("name") @param:JsonProperty("name") open val name: String,
        @field:JsonProperty("unique_id") @param:JsonProperty("unique_id") open val uniqueId: String,
        @field:JsonProperty("platform") @param:JsonProperty("platform") val platform: Platform,
    )

    open class Component(
        @field:JsonProperty("name") @param:JsonProperty("name") val name: String?,

        @field:JsonProperty("unique_id") @param:JsonProperty(
            "unique_id"
        ) val uniqueId: String?,

        @field:JsonProperty("platform") @param:JsonProperty(
            "platform"
        ) val platform: Platform?,

        @field:JsonProperty("device_class") @param:JsonProperty(
            "device_class"
        ) val deviceClass: DeviceClass?,

        @field:JsonProperty("state_class") @param:JsonProperty(
            "state_class"
        ) val stateClass: StateClass?,

        @field:JsonProperty("unit_of_measurement") @param:JsonProperty(
            "unit_of_measurement"
        ) val unitOfMeasurement: String?,

        @field:JsonProperty("suggested_display_precision") @param:JsonProperty(
            "suggested_display_precision"
        ) val suggestedDisplayPrecision: Int,

        @field:JsonProperty("value_template") @param:JsonProperty(
            "value_template"
        ) val valueTemplate: String?
    ) {
        /**
         * See [docs](https://developers.home-assistant.io/docs/core/entity/sensor/)
         */
        enum class DeviceClass {
            POWER,
            CURRENT,
            FREQUENCY,
            ENERGY,
            VOLTAGE;

            @JsonValue
            fun getName(): String {
                return name.lowercase(Locale.getDefault())
            }
        }

        /**
         * See [docs](https://developers.home-assistant.io/docs/core/entity/sensor/)
         */
        enum class StateClass {
            MEASUREMENT,
            TOTAL_INCREASING;

            @JsonValue
            fun getName(): String {
                return name.lowercase(Locale.getDefault())
            }
        }

        enum class Platform {
            SENSOR,
            SWITCH;

            @JsonValue
            fun getName(): String {
                return name.lowercase(Locale.getDefault())
            }
        }
    }
}

//class DiscoveryHelper {
//    fun genDiscovery(discovery: Discovery): MqttMessage {
//        return MqttPublishMessage.create(
//            "homeassistant/device/" + DEVICE_NAME + "/" + discovery.device!!.identifiers + "/config",
//            discovery,
//            MqttQoS.AT_LEAST_ONCE,
//            true
//        )
//    }
//
//    companion object {
//        var DEVICE_NAME: String = "alfen-mqtt"
//    }
//}