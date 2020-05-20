package org.muizenhol.qbus.sddata

import com.fasterxml.jackson.module.kotlin.readValue
import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.invoke.MethodHandles
import java.util.zip.ZipInputStream


class SdDataParser() {
    companion object {
        val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    var data = ByteArray(0)

    fun addData(data: ByteArray) {
        this.data += data
    }

    fun parse(): SdDataStruct? {
        LOG.info("Unzipping SD data")
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                LOG.debug("Zip Entry: {}", zipEntry.name)
                if (zipEntry.name.equals("JSONData.tmp")) {
                    val out = ByteArrayOutputStream()
                    zis.transferTo(out)
                    return parse(out.toByteArray())
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
        return null
    }

    //fun parse(file: File) {
    fun parse(stream: InputStream): SdDataStruct {
        return parse(Common.OBJECT_MAPPER.readValue<SdDataJson>(stream))
    }

    fun parse(stream: ByteArray): SdDataStruct {
        return parse(Common.OBJECT_MAPPER.readValue<SdDataJson>(stream))
    }

    private fun parse(data: SdDataJson): SdDataStruct {
        LOG.info("SD data: version: {} -- serial: {}", data.version, data.serialNumber)

        val placesMap = data.places.map {
            SdDataStruct.Place(
                name = it.name,
                id = it.id
            )
        }.associateBy { it.id }

        val outputs = data.outputs.map { output ->
            SdDataStruct.Output(
                name = output.originalName,
                id = output.id,
                address = output.address.toByte(),
                subAddress = output.subAddress.toByte(),
                controllerId = output.controllerId,
                place = placesMap.getOrDefault(output.placeId, SdDataStruct.Place(-1, "Unknown")),
                type = SdDataStruct.Type.values()
                    .toList()
                    .filter { it.id == output.typeId }
                    .firstOrNull() ?: SdDataStruct.Type.UNKNOWN
            )
        }.associateBy { it.id }

        val d = SdDataStruct(
            version = data.version,
            serialNumber = data.serialNumber,
            places = placesMap,
            outputs = outputs
        )
        d.outputs.values.sortedBy { x -> x.address }
            .forEach {
                LOG.info(
                    "Output {} - {} ({})",
                    Common.byteToHex(it.address),
                    Common.byteToHex(it.subAddress),
                    it.name
                )
            }
        return d
    }
}

data class SdDataStruct(
    val version: String,
    val serialNumber: String,
    val places: Map<Int, Place>,
    val outputs: Map<Int, Output>
    /** key: id */
) {
    data class Place(
        val id: Int,
        val name: String
    )

    enum class Type(val id: Int) { //TODO: not sure if those id's are fixed?
        UNKNOWN(-1),
        ON_OFF(1),
        DIMMER1B(3), //1 button dimmer
        DIMMER2B(4), //2 button dimmier
        TIMER(5),
        SHUTTER(24),
        THERMOSTAT(25),
        THERMOSTAT2(15), //no clue what the difference is with the other thermostat
    }

    data class Output(
        val id: Int,
        val name: String,
        val address: Byte,
        val subAddress: Byte,
        val controllerId: Int,
        val place: Place,
        val type: Type
    ) {
        var value: Byte? = null
    }

}
