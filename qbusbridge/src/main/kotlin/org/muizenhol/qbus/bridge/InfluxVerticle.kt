package org.muizenhol.qbus.bridge


import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.write.Point
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.bridge.type.MqttSensorItem
import org.muizenhol.qbus.sddata.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles


class InfluxVerticle(private val token: String?, private val url: String?) : AbstractVerticle() {
    lateinit var client: InfluxDBClient
    lateinit var consumer: MessageConsumer<MqttItemWrapper>
    lateinit var consumerSensor: MessageConsumer<MqttSensorItem>
    private val bucket = "domotica"
    val org = "muizenhol"

    override fun start(startPromise: Promise<Void>) {
        LOG.info("Starting")
        if (token == null || url == null) {
            LOG.warn("Influx token/url not set. Not starting influx verticle")
            startPromise.complete()
            return
        }

        client = InfluxDBClientFactory.create(url, token.toCharArray(), org)
        consumer = vertx.eventBus().localConsumer(MqttVerticle.ADDRESS, this::handle)
        consumerSensor = vertx.eventBus().localConsumer(MqttVerticle.ADDRESS_SENSOR, this::handle2)
        startPromise.complete()
        LOG.info("InfluxDb client created")
    }

    /**
     * Qbus to Influx
     */
    private fun handle(msg: Message<MqttItemWrapper>) {
        val serial = msg.body().serial
        val data = msg.body().data
        val point: Point = Point
            .measurement(data.name)
            .addTag("serial", serial)
            .addTag("type", data.typeName)
        when (data) {
            is SdOutputOnOff -> point.addField("value", data.asInt())
            is SdOutputDimmer -> point.addField("value", data.asInt())
            is SdOutputTimer -> point.addField("value", data.asInt())
            is SdOutputTimer2 -> point.addField("value", data.asInt())
            is SdOutputThermostat -> point
                .addField("set", data.getTempSet())
                .addField("measured", data.getTempMeasured())
            is SdOutputAudio -> return
        }

        LOG.debug("Writing qbus point for {}", data.name)
        client.getWriteApi().use { writeApi -> writeApi.writePoint(bucket, org, point) }
    }

    /**
     * Openhab sensor to Influx
     */
    private fun handle2(msg: Message<MqttSensorItem>) {
        val data = msg.body()
        val point: Point = Point
            .measurement(data.name)
            .addField(data.sensor, data.data)

        LOG.trace("Writing sensor point for {}", data.name)
        client.getWriteApi().use { writeApi -> writeApi.writePoint(bucket, org, point) }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}