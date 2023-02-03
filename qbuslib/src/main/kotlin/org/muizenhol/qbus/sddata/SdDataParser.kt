package org.muizenhol.qbus.sddata

import com.fasterxml.jackson.module.kotlin.readValue
import org.muizenhol.qbus.Common
import org.muizenhol.qbus.datatype.AddressStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.invoke.MethodHandles
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt


class SdDataParser {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
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

    fun parse(stream: InputStream): SdDataStruct {
        return parse(Common.OBJECT_MAPPER.readValue<SdDataJson>(stream))
    }

    fun parse(stream: ByteArray): SdDataStruct {
        return parse(Common.OBJECT_MAPPER.readValue<SdDataJson>(stream))
    }

    private fun isReadOnly(name: String): Boolean {
        //TODO: make dynamic
        return name.lowercase().startsWith("actuator_")
                || name.lowercase().startsWith("wp_")
                || name.lowercase().startsWith("z_")
    }

    private fun toOutput(outJson: SdDataJson.Outputs, place: SdDataStruct.Place): SdOutput? {
        val type = SdDataStruct.Type.values()
            .toList()
            .filter { it.id == outJson.typeId }
            .firstOrNull() ?: SdDataStruct.Type.UNKNOWN
        return when (type) {
            SdDataStruct.Type.ON_OFF
            -> SdOutputOnOff(
                name = outJson.originalName,
                id = outJson.id,
                address = outJson.address.toByte(),
                subAddress = outJson.subAddress.toByte(),
                controllerId = outJson.controllerId,
                place = place,
                readonly = isReadOnly(outJson.originalName),
            )
            SdDataStruct.Type.TIMER
            ->
                SdOutputTimer(
                name = outJson.originalName,
                id = outJson.id,
                address = outJson.address.toByte(),
                subAddress = outJson.subAddress.toByte(),
                controllerId = outJson.controllerId,
                place = place,
                readonly = isReadOnly(outJson.originalName),
            )
            SdDataStruct.Type.TIMER2
            -> SdOutputTimer2(
                name = outJson.originalName,
                id = outJson.id,
                address = outJson.address.toByte(),
                subAddress = outJson.subAddress.toByte(),
                controllerId = outJson.controllerId,
                place = place,
                readonly = isReadOnly(outJson.originalName),
            )
            SdDataStruct.Type.DIMMER1B,
            SdDataStruct.Type.DIMMER2B
            -> SdOutputDimmer(
                name = outJson.originalName,
                id = outJson.id,
                address = outJson.address.toByte(),
                subAddress = outJson.subAddress.toByte(),
                controllerId = outJson.controllerId,
                place = place,
                readonly = isReadOnly(outJson.originalName),
            )
            SdDataStruct.Type.THERMOSTAT,
            SdDataStruct.Type.THERMOSTAT2,
            -> {
                if (outJson.subAddress != 0) {
                    throw IllegalArgumentException("Invalid subaddress: " + outJson.subAddress)
                }
                SdOutputThermostat(
                    name = outJson.originalName,
                    id = outJson.id,
                    address = outJson.address.toByte(),
                    controllerId = outJson.controllerId,
                    place = place,
                    readonly = isReadOnly(outJson.originalName)
                )
            }
            else -> {
                LOG.info("Unknown type: {} -- {} (address: {}, subaddress: {})", outJson.typeId,
                    outJson.originalName,
                    Common.byteToHex(outJson.address.toByte()), Common.byteToHex(outJson.subAddress.toByte()))
                null
            }
        }
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
            val place = placesMap.getOrDefault(output.placeId, SdDataStruct.Place(-1, "Unknown"))
            toOutput(output, place)
        }.filterNotNull().associateBy { it.id }

        val d = SdDataStruct(
            version = data.version,
            serialNumber = data.serialNumber,
            places = placesMap,
            outputs = outputs
        )
        d.outputs.values.sortedBy { x -> x.address }
            .forEach {
                LOG.info(
                    "Output {}: {} {} ({})",
                    it.typeName,
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
    val outputs: Map<Int, SdOutput>
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
        DIMMER2B(4), //2 button dimmer
        TIMER(5),
        TIMER2(6), //Staircase timer. Resets to max value on each press.
        SHUTTER(24),
        THERMOSTAT(25),
        THERMOSTAT2(15), //no clue what the difference is with the other thermostat
    }
}


sealed class SdOutput {
    abstract val id: Int
    abstract val name: String
    abstract val address: Byte
    abstract val subAddress: Byte
    abstract val controllerId: Int
    abstract val place: SdDataStruct.Place
    abstract val readonly: Boolean
    abstract val typeName: String
    abstract fun clone(): SdOutput
    abstract fun getAddressStatus(): AddressStatus
    abstract fun printValue(): String

    /**
     * Update value.
     * @param newData The new data
     * @param event: True if the data is an event, false if it's an addressStatus
     * @return true if update was done. False if value was already set.
     */
    abstract fun update(newData: ByteArray, event: Boolean): Boolean

    abstract fun update(newData: SdOutput): Boolean

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    protected fun singleValueUpdate(sub: SingleValue, newData: ByteArray): Boolean {
        LOG.debug("Updating {} {} ({})", typeName, subAddress, name)
        if (subAddress >= newData.size) {
            LOG.warn("Unexpected subaddress {} on {} ({})", subAddress, address, name)
            return false
        }
        val valueNew = newData[subAddress.toInt()]
        if (sub.value == valueNew) {
            LOG.trace("Not updating value for {} since identical", name)
            return false //Nothing to do
        }
        sub.value = valueNew
        return true
    }

    protected fun singleValueGetAddressStatus(sub: SingleValue): AddressStatus =
        AddressStatus(
            address,
            subAddress,
            data = byteArrayOf(0x00, sub.value),
            write = true
        )
}

interface SingleValue {
    var value: Byte
}


data class SdOutputOnOff(
    override val id: Int,
    override val name: String,
    override val address: Byte,
    override val subAddress: Byte,
    override val controllerId: Int,
    override val place: SdDataStruct.Place,
    override val readonly: Boolean,
) : SdOutput(), SingleValue {
    override var value: Byte = 0x00
    override val typeName = "OnOff"

    override fun clone(): SdOutput = copy()
    override fun getAddressStatus(): AddressStatus = singleValueGetAddressStatus(this)
    override fun printValue(): String = "0x" + Common.byteToHex(value)
    override fun update(newData: ByteArray, event: Boolean): Boolean {
        return singleValueUpdate(this, newData)
    }
    fun asInt(): Int = (value.toInt() and 0xff)

    override fun update(newData: SdOutput): Boolean {
        if (newData is SdOutputOnOff) {
            if (value != newData.value) {
                value = newData.value
                return true
            }
        } else {
            LOG.warn("Invalid data type: {}", newData.javaClass)
        }
        return false
    }
}

data class SdOutputTimer(
    override val id: Int,
    override val name: String,
    override val address: Byte,
    override val subAddress: Byte,
    override val controllerId: Int,
    override val place: SdDataStruct.Place,
    override val readonly: Boolean,
) : SdOutput(), SingleValue {
    override var value: Byte = 0x00
    override val typeName = "timer"

    override fun clone(): SdOutput = copy()
    override fun getAddressStatus(): AddressStatus = singleValueGetAddressStatus(this)
    override fun printValue(): String = "0x" + Common.byteToHex(value)
    override fun update(newData: ByteArray, event: Boolean): Boolean {
        val updated = singleValueUpdate(this, newData)
        if (value > 0x00) {
            // this value probably means how long the timer will be running
            // but since I don't understand it properly, just model as on-off
            value = 0xFF.toByte()
        }
        return updated
    }
    fun asInt(): Int = (value.toInt() and 0xff)

    override fun update(newData: SdOutput): Boolean {
        if (newData is SdOutputTimer) {
            if (value != newData.value) {
                value = newData.value
                return true
            }
        } else {
            LOG.warn("Invalid data type: {}", newData.javaClass)
        }
        return false
    }
}

//Timer2: resets to max value on each press.
data class SdOutputTimer2(
    override val id: Int,
    override val name: String,
    override val address: Byte,
    override val subAddress: Byte,
    override val controllerId: Int,
    override val place: SdDataStruct.Place,
    override val readonly: Boolean,
) : SdOutput(), SingleValue {
    override var value: Byte = 0x00
    override val typeName = "timer2"

    override fun clone(): SdOutput = copy()
    override fun getAddressStatus(): AddressStatus = singleValueGetAddressStatus(this)
    override fun printValue(): String = "0x" + Common.byteToHex(value)
    override fun update(newData: ByteArray, event: Boolean): Boolean {
        val updated = singleValueUpdate(this, newData)
        if (value > 0x00) {
            // this value probably means how long the timer will be running
            // but since I don't understand it properly, just model as on-off
            value = 0xFF.toByte()
        }
        return updated
    }
    fun asInt(): Int = (value.toInt() and 0xff)

    override fun update(newData: SdOutput): Boolean {
        if (newData is SdOutputTimer2) {
            if (value != newData.value) {
                value = newData.value
                return true
            }
        } else {
            LOG.warn("Invalid data type: {}", newData.javaClass)
        }
        return false
    }
}

data class SdOutputDimmer(
    override val id: Int,
    override val name: String,
    override val address: Byte,
    override val subAddress: Byte,
    override val controllerId: Int,
    override val place: SdDataStruct.Place,
    override val readonly: Boolean,
) : SdOutput(), SingleValue {
    override var value: Byte = 0x00
    override val typeName = "Dimmer"

    override fun clone(): SdOutput = copy()
    override fun getAddressStatus(): AddressStatus = singleValueGetAddressStatus(this)
    override fun printValue(): String = "0x" + Common.byteToHex(value)
    override fun update(newData: ByteArray, event: Boolean): Boolean {
        return singleValueUpdate(this, newData)
    }
    fun asInt(): Int = (value.toInt() and 0xff)

    override fun update(newData: SdOutput): Boolean {
        if (newData is SdOutputDimmer) {
            if (value != newData.value) {
                value = newData.value
                return true
            }
        } else {
            LOG.warn("Invalid data type: {}", newData.javaClass)
        }
        return false
    }
}

data class SdOutputThermostat(
    override val id: Int,
    override val name: String,
    override val address: Byte,
    override val controllerId: Int,
    override val place: SdDataStruct.Place,
    override val readonly: Boolean,
) : SdOutput() {
    private var value1: Byte = 0x00
    private var tempSet: Byte = 0x00
    private var tempMeasured: Byte = 0x00
    private var mode: Mode= Mode.OFF
    override val subAddress = 0x00.toByte()
    override val typeName = "Thermostat"
    override fun clone(): SdOutput {
        return copy()
    }

    override fun getAddressStatus(): AddressStatus =
        AddressStatus(
            address,
            0x01, //only tempset is being written, which lives at subAddress 0x01
            data = byteArrayOf(0x00, tempSet),
            write = true
        )

    private fun toTemp(value: Byte): Double {
        val valueInt = (value.toInt() and 0xff)
        return valueInt.toDouble() / 2
    }

    fun setTemp(value: Double) {
        tempSet = (value * 2).roundToInt().toByte()
    }

    fun getTempMeasured(): Double = toTemp(tempMeasured)
    fun getTempSet(): Double = toTemp(tempSet)

    override fun printValue(): String =
        "Set: ${getTempSet()} -- Measured: ${getTempMeasured()} -- Mode: ${mode} " +
                "(0X${Common.byteToHex(value1)})"

    override fun update(newData: ByteArray, event: Boolean): Boolean {
        //it means "??" "set" "current" "??"
        //where temperate needs to be divided by 2 to get the actual value
        if (newData.size != 4) {
            LOG.warn("Illegal value for Thermostat: ${Common.bytesToHex(newData)}")
            return false;
        }
        var changed = false
        val data = if (event) {
            newData
        } else {
            //data is ordered differently when received from addressStatus as from event. Re-order.
            byteArrayOf(0x00, newData[0], newData[1], newData[2])
        }
        if (value1 != data[0]) {
            value1 = data[0]
            changed = true
        }
        if (tempSet != data[1]) {
            tempSet = data[1]
            changed = true
        }
        if (tempMeasured != data[2]) {
            tempMeasured = data[2]
            changed = true
        }
        val newMode = Mode.fromId(data[3])
        if (mode != newMode) {
            mode = newMode
            changed = true
        }
        return changed
    }

    override fun update(newData: SdOutput): Boolean {
        if (newData is SdOutputThermostat) {
            if (value1 != newData.value1 ||
                tempMeasured != newData.tempMeasured ||
                tempSet != newData.tempSet ||
                mode != newData.mode) {
                value1 = newData.value1
                tempMeasured = newData.tempMeasured
                tempSet = newData.tempSet
                mode = newData.mode
                return true
            }
        } else {
            LOG.warn("Invalid data type: {}", newData.javaClass)
        }
        return false
    }

    enum class Mode(val id: Byte) {
        OFF(0x00),
        FREEZE(0x01),
        ECONOMY(0x02),
        COMFORT(0x03),
        NIGHT(0x04);

        companion object {
            fun fromId(id: Byte): Mode {
                return values().iterator()
                    .asSequence()
                    .find { it.id == id } ?: OFF
            }
        }

    }
}

