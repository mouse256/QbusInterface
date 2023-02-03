package org.muizenhol.qbus.bridge


import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.bridge.type.MqttSensorItem
import org.muizenhol.qbus.sddata.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient
import software.amazon.awssdk.services.timestreamwrite.model.*
import java.lang.invoke.MethodHandles
import java.time.Duration
import java.time.Instant
import java.util.function.Consumer


class TimestreamVerticle(val environment: Environment, val accessKey: String?, val accessSecret: String?) :
    AbstractVerticle() {
    lateinit var consumer: MessageConsumer<MqttItemWrapper>
    lateinit var consumerSensor: MessageConsumer<MqttSensorItem>
    lateinit var timestreamWriteClient: TimestreamWriteClient
    lateinit var dbName: String
    lateinit var tableName: String

    override fun start(startPromise: Promise<Void>) {
        LOG.info("Starting")
        if (accessKey == null || accessSecret == null) {
            LOG.warn("AWS access key/secret not set. Not starting timestream verticle")
            startPromise.complete()
            return
        }
        dbName = "$DATABASE_NAME-${environment.name.toLowerCase()}"
        tableName = "$TABLE_NAME-${environment.name.toLowerCase()}"
        vertx.executeBlocking({
            timestreamWriteClient = buildWriteClient()
            val tName = timestreamWriteClient
                .describeTable(
                    DescribeTableRequest.builder()
                        .databaseName(dbName)
                        .tableName(tableName)
                        .build()
                )
                .table()
                .tableName()
            LOG.info("TimeStream table name: {}", tName)

            consumer = vertx.eventBus().localConsumer(MqttVerticle.ADDRESS, this::handle)
            consumerSensor = vertx.eventBus().localConsumer(MqttVerticle.ADDRESS_SENSOR, this::handle2)
            it.complete()
        }, startPromise::handle)
    }

    /**
     * Recommended Timestream write client SDK configuration:
     * - Set SDK retry count to 10.
     * - Use SDK DEFAULT_BACKOFF_STRATEGY
     * - Set RequestTimeout to 20 seconds .
     * - Set max connections to 5000 or higher.
     */
    private fun buildWriteClient(): TimestreamWriteClient {
        val httpClientBuilder: ApacheHttpClient.Builder = ApacheHttpClient.builder().maxConnections(5)
        val retryPolicy: RetryPolicy.Builder = RetryPolicy.builder().numRetries(3)
        val overrideConfig: ClientOverrideConfiguration = ClientOverrideConfiguration.builder()
            .apiCallAttemptTimeout(Duration.ofSeconds(5))
            .retryPolicy(retryPolicy.build())
            .build()
        val clientBuilder = TimestreamWriteClient.builder()
        accessKey?.let {
            accessSecret?.let {
                //when credentials provided, use those. Otherwise use system creds.
                LOG.info("AWS credentials provided, using those.")
                clientBuilder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, accessSecret))
                )
            }
        }
        return clientBuilder
            .httpClientBuilder(httpClientBuilder)
            .overrideConfiguration(overrideConfig)
            .region(Region.EU_WEST_1)
            .build()
    }

    /**
     * Qbus to TimeStream
     */
    private fun handle(msg: Message<MqttItemWrapper>) {
        val serial = msg.body().serial
        val data = msg.body().data

        Dimension.builder()
            .name("type").value("watertemp")
            .build()

        val commonAttributes: Record = Record.builder()
            .dimensions(
                Dimension.builder().name("serial").value(serial).build(),
                Dimension.builder().name("type").value(data.typeName).build(),
                Dimension.builder().name("name").value(data.name).build(),
            )
            .measureValueType(MeasureValueType.DOUBLE) //there is no int value
            .time(Instant.now().toEpochMilli().toString())
            .timeUnit(TimeUnit.MILLISECONDS)
            .build()


        val recordBuilder = WriteRecordsRequest.builder()
            .databaseName(dbName)
            .tableName(tableName)
            .commonAttributes(commonAttributes)

        val recordsRaw: List<Record> = when (data) {
            is SdOutputOnOff -> listOf(
                Record.builder().measureName("output").measureValue(data.asInt().toString()).build()
            )
            is SdOutputTimer -> listOf(
                Record.builder().measureName("output").measureValue(data.asInt().toString()).build()
            )
            is SdOutputTimer2 -> listOf(
                Record.builder().measureName("output").measureValue(data.asInt().toString()).build()
            )
            is SdOutputDimmer -> listOf(
                Record.builder().measureName("output").measureValue(data.asInt().toString()).build()
            )
            is SdOutputThermostat -> {
                listOf(
                    Record.builder().measureName("set").measureValue(data.getTempSet().toString()).build(),
                    Record.builder().measureName("measured").measureValue(data.getTempMeasured().toString()).build()
                )
            }
        }
        val records = recordBuilder
            .records(recordsRaw)
            .build()

        LOG.debug("Writing qbus point for {}", data.name)
        try {
            val writeRecordsResponse: WriteRecordsResponse = timestreamWriteClient.writeRecords(records)
            LOG.info(
                "writeRecordsWithCommonAttributes Status: {}", writeRecordsResponse.sdkHttpResponse().statusCode()
            )
        } catch (e: RejectedRecordsException) {
            e.rejectedRecords().forEach(Consumer { r: RejectedRecord? ->
                LOG.warn("REJECTED: {}", r)
            })
            throw RuntimeException(e)
        }
    }

    /**
     * Openhab sensor to Influx
     */
    private fun handle2(msg: Message<MqttSensorItem>) {
        /*val data = msg.body()
        val point: Point = Point
            .measurement(data.name)
            .addField(data.sensor, data.data)

        LOG.trace("Writing sensor point for {}", data.name)
        client.getWriteApi().use { writeApi -> writeApi.writePoint(bucket, org, point) }*/
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val DATABASE_NAME = "iotDatabase"
        private val TABLE_NAME = "qbus"
    }
}