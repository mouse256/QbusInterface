package org.muizenhol.qbus.sddata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
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
                place = placesMap.getOrDefault(output.placeId, SdDataStruct.Place(-1, "Unknown"))
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
    val serialNumber: Int,
    val places: Map<Int, Place>,
    val outputs: Map<Int, Output>
) {
    data class Place(
        val id: Int,
        val name: String
    )

    data class Output(
        val id: Int,
        val name: String,
        val address: Byte,
        val subAddress: Byte,
        val controllerId: Int,
        val place: Place

    ) {
        var value: Byte = 0
        fun updateValue(value: Byte) {
            SdDataParser.LOG.info(
                "Update value for {} from {} to {}",
                name,
                Common.byteToHex(this.value),
                Common.byteToHex(value)
            )
            this.value = value
        }
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SdDataJson(
    @JsonProperty("Version") val version: String,
    @JsonProperty("SerialNumber") val serialNumber: Int,
    @JsonProperty("Places") val places: List<Place>,
    @JsonProperty("Outputs") val outputs: List<Outputs>
) {
    data class Place(
        @JsonProperty("ID") val id: Int,
        @JsonProperty("ParentID") val parentId: Int,
        @JsonProperty("Name") val name: String
    )

    data class Outputs(
        @JsonProperty("Address") val address: Int,
        @JsonProperty("SubAddress") val subAddress: Int,
        @JsonProperty("ControllerId") val controllerId: Int,
        @JsonProperty("ID") val id: Int,
        @JsonProperty("OriginalName") val originalName: String,
        @JsonProperty("ShortName") val shortName: String,
        @JsonProperty("TypeId") val typeId: Int,
        @JsonProperty("Real") val real: Boolean,
        @JsonProperty("System") val system: Boolean,
        @JsonProperty("EventsOnSD") val eventsOnSd: Boolean,
        @JsonProperty("PlaceId") val placeId: Int,
        @JsonProperty("IconNr") val iconNr: Int,
        @JsonProperty("RangeMin") val rangeMin: Int?,
        @JsonProperty("RangeMax") val rangeMax: Int?,
        @JsonProperty("Correction") val correction: Int?,
        @JsonProperty("Offset") val offset: Int?,
        @JsonProperty("HasSensor") val hasSensor: Boolean?,
        @JsonProperty("Unit") val unit: String?
    )
}
