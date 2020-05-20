package org.muizenhol.qbus

import org.muizenhol.qbus.datatype.AddressStatus
import org.muizenhol.qbus.datatype.Event
import org.muizenhol.qbus.sddata.SdDataParser
import org.muizenhol.qbus.sddata.SdDataStruct

class DataHandler(val data: SdDataStruct) {

    /*interface Listener {
        fun onEvent(event: DataType)
    }*/
    var listener: (String, SdDataStruct.Output) -> Unit = { _, _ -> }

    fun setEventListener(listener: (String, SdDataStruct.Output) -> Unit) {
        this.listener = listener
    }

    fun getOutput(id: Int): SdDataStruct.Output? {
        return data.outputs.get(id)?.copy()
    }

    private fun updateValue(output: SdDataStruct.Output, value: Byte) {
        if (value == output.value) {
            SdDataParser.LOG.trace("Not updating value for {} since identical", output.name)
            return //Nothing to do
        }
        SdDataParser.LOG.info(
            "Update value for {} from {} to {}",
            output.name,
            output.value?.let { v -> Common.byteToHex(v) },
            Common.byteToHex(value)
        )
        output.value = value
        listener.invoke(data.serialNumber, output)
    }

    private fun update(address: Byte, newData: ByteArray) {
        data.outputs.filterValues { v -> v.address == address }
            .forEach { _, v ->
                SdDataParser.LOG.info("Updating {} ({})", v.subAddress, v.name)
                if (v.subAddress >= newData.size) {
                    SdDataParser.LOG.warn("Unexpected subaddress {} on {} ({})", v.subAddress, v.address, v.name)
                } else {
                    updateValue(v, newData[v.subAddress.toInt()])
                }
                //TODO update parsing: eg thermostats are incorrect.
                //Data 2a 2a 02 00 means "setpoint" "current" "state" "unknown"
                //where temperate needs to be divided by 2 to get the actual value
            }
    }

    fun update(event: AddressStatus) {
        if (event.subAddress != 0xFF.toByte()) {
            throw IllegalStateException("Can't handle this subaddress")
        }
        update(event.address, event.data)
    }

    fun update(event: Event) {
        update(event.address, event.data)
    }

    fun update(id: Int, value: Byte) {
        data.outputs.get(id)?.let { out ->
            updateValue(out, value)
        }
    }


}