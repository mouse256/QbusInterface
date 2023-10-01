package org.muizenhol.qbus.sddata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SdDataJson(
    @JsonProperty("Version") val version: String,
    @JsonProperty("SerialNumber") val serialNumber: String,
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
        @JsonProperty("RangeMin") val rangeMin: Int? = null,
        @JsonProperty("RangeMax") val rangeMax: Int? = null,
        @JsonProperty("Correction") val correction: Int? = null,
        @JsonProperty("Offset") val offset: Int? = null,
        @JsonProperty("HasSensor") val hasSensor: Boolean? = null,
        @JsonProperty("Unit") val unit: String? = null,
        @JsonProperty("NumberOfColours") val numberOfColours: Int? = null,
        @JsonProperty("VolumeUpId") val volumeUpId: Int,
        @JsonProperty("VolumeDownId") val volumeDownId: Int,
        @JsonProperty("PlayPauseId") val playPauseId: Int,
        @JsonProperty("FavoritesId") val favoritesId: Int,
    )
}
