package org.muizenhol.qbus.bridge

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.muizenhol.qbus.Common
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.bridge.openhab.OpenHabFormatter
import org.muizenhol.qbus.sddata.SdDataJson
import org.muizenhol.qbus.sddata.SdDataParser
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles


class OpenHabTest() {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val CONTROLLER_ID = 123
    }

    private var currentAddress = 0
    private var currentId = 1000


    @Test
    @Disabled
    fun testThings() {
        val parser = SdDataParser()
        val stream = this.javaClass.getResourceAsStream("/sddata.zip")
            ?: throw IllegalStateException("Can't find resource sddata.zip")
        parser.addData(stream.readAllBytes())
        val parsed = parser.parse() ?: throw IllegalStateException("Can't parse data")
        val dataHandler = DataHandler(parsed)

        val formatter = OpenHabFormatter(dataHandler.data, "0.0.0.0")
        val things = formatter.asThings()
        LOG.info("things: \n{}", things)
    }

    private fun createOutput(type: SdDataStruct.Type, name: String): SdDataJson.Outputs {
        val out = createOutput(type, name, currentAddress, 0, currentId)
        currentId++
        currentAddress++
        return out
    }

    private fun createOutput(
        type: SdDataStruct.Type, name: String, address: Int, subAddress: Int, id: Int,
        volumeUpId: Int = 0,
        volumeDownId: Int = 0,
        playPauseId: Int = 0,
        favoritesId: Int = 0,
    ): SdDataJson.Outputs {
        return SdDataJson.Outputs(
            address = address,
            subAddress = subAddress,
            controllerId = CONTROLLER_ID,
            id = id,
            name,
            name,
            typeId = type.id,
            real = true,
            system = false,
            eventsOnSd = false,
            placeId = 1,
            iconNr = 1,
            volumeUpId = volumeUpId,
            volumeDownId = volumeDownId,
            playPauseId = playPauseId,
            favoritesId = favoritesId
        )
    }

    private fun fakeData(): SdDataStruct {
        val parser = SdDataParser()
        val out1 = createOutput(SdDataStruct.Type.ON_OFF, "output1")
        val out2 = createOutput(SdDataStruct.Type.ON_OFF, "output2")
        val out3 = createOutput(SdDataStruct.Type.THERMOSTAT, "thermostat 1")
        val out4 = createOutput(SdDataStruct.Type.DIMMER1B, "dimmer 1")
        val out5 = createOutput(SdDataStruct.Type.DIMMER2B, "dimmer 2")
        val data = SdDataJson(
            "fake", "887766", emptyList(),
            listOf(
                out1, out2, out3, out4, out5
            )
        )
        val parsed = parser.parse(Common.OBJECT_MAPPER.writeValueAsBytes(data))
        val dataHandler = DataHandler(parsed)
        return dataHandler.data
    }

    private fun fakeAudioData(): SdDataStruct {
        val parser = SdDataParser()
        val badVolDown = createOutput(SdDataStruct.Type.AUDIO, "audio_badkamer (Vol-)")
        val badVolUp = createOutput(SdDataStruct.Type.AUDIO, "audio_badkamer (Vol+)")
        val badVolFav = createOutput(SdDataStruct.Type.AUDIO, "audio_badkamer (Fav.)")
        val badVolPlayPause = createOutput(SdDataStruct.Type.ON_OFF, "audio_badkamer")
        val audioBadkamer = createOutput(
            SdDataStruct.Type.AUDIO2, "audio_badkamer",
            currentAddress, 0, currentId,
            volumeUpId = badVolUp.id,
            volumeDownId = badVolDown.id,
            playPauseId = badVolPlayPause.id,
            favoritesId = badVolFav.id
        )
        currentAddress++
        currentId++
        val data = SdDataJson(
            "fake", "887766", emptyList(),
            listOf(
                badVolDown, badVolUp, badVolFav, badVolPlayPause, audioBadkamer
            )
        )
        val parsed = parser.parse(Common.OBJECT_MAPPER.writeValueAsBytes(data))
        val dataHandler = DataHandler(parsed)
        return dataHandler.data
    }

    @Test
    fun testAudioThings() {
        val data = fakeAudioData()
        val formatter = OpenHabFormatter(data, "0.0.0.0")
        val things = formatter.asThings()
        LOG.debug("things: \n{}", things)
        val expected = """
            Bridge mqtt:broker:qbusBroker [ host="0.0.0.0", secure=false] {
              Thing topic qbus_thing_audio_badkamer "audio_badkamer" @ "QBus" {
                Channels:
                  Type switch: play [stateTopic="qbus/887766/sensor/switch/1003/state", commandTopic="qbus/887766/sensor/switch/1003/command", off="0", on="255"]
                  Type trigger: vol_up [stateTopic="qbus/887766/sensor/event/1001/event"]
                  Type trigger: vol_down [stateTopic="qbus/887766/sensor/event/1000/event"]
              }
            }
            
            """.trimIndent()
        assertThat(things, equalTo(expected))
    }

    @Test
    fun testAudioItems() {
        val data = fakeAudioData()
        val formatter = OpenHabFormatter(data, "0.0.0.0")
        val items = formatter.asItems()
        LOG.debug("items: \n{}", items)

        val expected = """
            Switch qbus_item_audio_badkamer "audio_badkamer" {channel="mqtt:topic:qbusBroker:qbus_thing_audio_badkamer:play"}
            
            """.trimIndent()
        assertThat(items, equalTo(expected))
    }

    @Test
    fun testOnOffThings() {
        val data = fakeData()
        val formatter = OpenHabFormatter(data, "0.0.0.0")
        val things = formatter.asThings()
        LOG.info("things: \n{}", things)
        val expected = """
            Bridge mqtt:broker:qbusBroker [ host="0.0.0.0", secure=false] {
              Thing topic qbus_thing_dimmer_1 "dimmer 1" @ "QBus" {
                Channels:
                  Type dimmer: qbus_channel_dimmer_1 [stateTopic="qbus/887766/sensor/dimmer/1003/state", commandTopic="qbus/887766/sensor/dimmer/1003/command", min="0", max="100", transformationPattern="JS:255to100.js", transformationPatternOut="JS:100to255.js"]
              }
              Thing topic qbus_thing_dimmer_2 "dimmer 2" @ "QBus" {
                Channels:
                  Type dimmer: qbus_channel_dimmer_2 [stateTopic="qbus/887766/sensor/dimmer/1004/state", commandTopic="qbus/887766/sensor/dimmer/1004/command", min="0", max="100", transformationPattern="JS:255to100.js", transformationPatternOut="JS:100to255.js"]
              }
              Thing topic qbus_thing_output1 "output1" @ "QBus" {
                Channels:
                  Type switch: qbus_channel_output1 [stateTopic="qbus/887766/sensor/switch/1000/state", commandTopic="qbus/887766/sensor/switch/1000/command", off="0", on="255"]
              }
              Thing topic qbus_thing_output2 "output2" @ "QBus" {
                Channels:
                  Type switch: qbus_channel_output2 [stateTopic="qbus/887766/sensor/switch/1001/state", commandTopic="qbus/887766/sensor/switch/1001/command", off="0", on="255"]
              }
              Thing topic qbus_thing_thermostat_1 "thermostat 1" @ "QBus" {
                Channels:
                  Type number: qbus_channel_thermostat_1_measured [stateTopic="qbus/887766/sensor/thermostat/1002/measured", min="0", max="30", step="0.5", unit="°C"]
                  Type number: qbus_channel_thermostat_1_set [stateTopic="qbus/887766/sensor/thermostat/1002/set", commandTopic="qbus/887766/sensor/thermostat/1002/command", min="0", max="30", step="0.5", unit="°C"]
              }
            }
            
            """.trimIndent()
        assertThat(things, equalTo(expected))
    }

    @Test
    fun testOnOffItems() {
        val data = fakeData()
        val formatter = OpenHabFormatter(data, "0.0.0.0")
        val items = formatter.asItems()
        LOG.info("items: \n{}", items)

        val expected = """
            Dimmer qbus_item_dimmer_1 "dimmer 1" {channel="mqtt:topic:qbusBroker:qbus_thing_dimmer_1:qbus_channel_dimmer_1"}
            Dimmer qbus_item_dimmer_2 "dimmer 2" {channel="mqtt:topic:qbusBroker:qbus_thing_dimmer_2:qbus_channel_dimmer_2"}
            Switch qbus_item_output1 "output1" {channel="mqtt:topic:qbusBroker:qbus_thing_output1:qbus_channel_output1"}
            Switch qbus_item_output2 "output2" {channel="mqtt:topic:qbusBroker:qbus_thing_output2:qbus_channel_output2"}
            Number qbus_item_thermostat_1_measured "Measured" {channel="mqtt:topic:qbusBroker:qbus_thing_thermostat_1:qbus_channel_thermostat_1_measured"}
            Number qbus_item_thermostat_1_set "Set" {channel="mqtt:topic:qbusBroker:qbus_thing_thermostat_1:qbus_channel_thermostat_1_set"}
            
            """.trimIndent()
        assertThat(items, equalTo(expected))
    }
}
