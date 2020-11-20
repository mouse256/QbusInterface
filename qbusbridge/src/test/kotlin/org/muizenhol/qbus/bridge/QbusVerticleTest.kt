package org.muizenhol.qbus.bridge

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.bridge.type.MqttItemWrapper
import org.muizenhol.qbus.sddata.*
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
@Timeout(10, timeUnit = TimeUnit.SECONDS)
class QbusVerticleTest() {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }


    @BeforeEach
    fun beforeEach(vertx: Vertx, vertxContext: VertxTestContext) {
        ControllerHandler.registerVertxCodecs(vertx)
        places.clear()
        outputs.clear()
        vertxContext.checkpoint().flag()
        vertx.exceptionHandler { ex ->
            LOG.warn("vertx exception", ex)
            vertxContext.failNow(ex)
        }
    }

    @AfterEach
    fun afterEach(vertx: Vertx, vertxContext: VertxTestContext) {
        vertxContext.checkpoint().flag()
        ControllerHandler.unregisterVertxCodecs(vertx)
        //val checkpoint = vertxContext.checkpoint()
    }

    lateinit var ctrl: Controller
    lateinit var qbusVerticle: QbusVerticle
    lateinit var prom: Promise<DataHandler>
    val mockPlace = SdDataStruct.Place(1, "mockPlace")
    val places = mutableMapOf<Int, SdDataStruct.Place>()
    val outputs = mutableMapOf<Int, SdOutput>()

    fun start(vertx: Vertx, vertxContext: VertxTestContext, onReady: () -> Unit) {
        val check = vertxContext.checkpoint()
        prom = Promise.promise()
        places.put(1, mockPlace)

        ctrl = mock {
            /*on { writeMsgVersion() } doAnswer {
                LOG.debug("Mocking version")
                sendEvent(Version("01.02", serial))
                void()
            }*/
            on { start() } doAnswer {
                LOG.info("[ctrl mock] start")
                try {
                    onReady()
                    check.flag()
                } catch (ex: Exception) {
                    vertxContext.failNow(ex)
                }
            }
        }
        val qbusVerticle = QbusVerticle(ctrl, prom.future())
        vertx.deployVerticle(qbusVerticle) { ar ->
            if (ar.failed()) {
                vertxContext.failNow(ar.cause())
            }
        }
    }

    private fun promComplete() {
        val dh = DataHandler(SdDataStruct("vMock", "serial", places, outputs))
        prom.complete(dh)
    }

    @Test
    fun testOnOff(vertx: Vertx, vertxContext: VertxTestContext) {
        val check = vertxContext.checkpoint()
        start(vertx, vertxContext) {
            vertx.eventBus().localConsumer<MqttItemWrapper>(MqttVerticle.ADDRESS) { msg ->
                try {
                    val item = msg.body()
                    assertThat(item.serial, equalTo("serial"))
                    val data = item.data
                    when (data) {
                        is SdOutputOnOff -> {
                            assertThat(data.id, equalTo(1))
                            assertThat(data.value, equalTo(0xFF.toByte()))
                            check.flag()
                        }
                        else -> throw IllegalStateException("Not expected")
                    }
                } catch (t: Throwable) {
                    vertxContext.failNow(t)
                }
            }

            val out1 = SdOutputOnOff(1, "out2", 0x51, 0x02, 123, mockPlace, false)
            out1.value = 0xFF.toByte()
            outputs.put(1, out1)

            promComplete()
        }
    }

    @Test
    fun testDimmer(vertx: Vertx, vertxContext: VertxTestContext) {
        val check = vertxContext.checkpoint()
        start(vertx, vertxContext) {
            vertx.eventBus().localConsumer<MqttItemWrapper>(MqttVerticle.ADDRESS) { msg ->
                try {
                    val item = msg.body()
                    assertThat(item.serial, equalTo("serial"))
                    val data = item.data
                    when (data) {
                        is SdOutputDimmer -> {
                            assertThat(data.id, equalTo(2))
                            assertThat(data.value, equalTo(0x55.toByte()))
                            check.flag()
                        }
                        else -> throw IllegalStateException("Not expected")
                    }
                } catch (t: Throwable) {
                    vertxContext.failNow(t)
                }
            }

            val out1 = SdOutputDimmer(2, "out2", 0x51, 0x02, 123, mockPlace, false)
            out1.value = 0x55.toByte()
            outputs.put(1, out1)

            promComplete()
        }
    }

    @Test
    fun testThermostat(vertx: Vertx, vertxContext: VertxTestContext) {
        val check = vertxContext.checkpoint()
        start(vertx, vertxContext) {
            vertx.eventBus().localConsumer<MqttItemWrapper>(MqttVerticle.ADDRESS) { msg ->
                try {
                    val item = msg.body()
                    assertThat(item.serial, equalTo("serial"))
                    val data = item.data
                    when (data) {
                        is SdOutputThermostat -> {
                            assertThat(data.id, equalTo(3))
                            assertThat(data.getTempMeasured(), equalTo(24.0))
                            assertThat(data.getTempSet(), equalTo(32.0))
                            check.flag()
                        }
                        else -> throw IllegalStateException("Not expected")
                    }
                } catch (t: Throwable) {
                    vertxContext.failNow(t)
                }
            }

            val out1 = SdOutputThermostat(3, "out3", 0x52, 123, mockPlace, false)
            out1.update(byteArrayOf(0x00, 0x40, 0x30, 0x02), true)
            outputs.put(1, out1)

            promComplete()
        }
    }


}
