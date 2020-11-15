package org.muizenhol.qbus

import org.muizenhol.qbus.datatype.AddressStatus
import org.muizenhol.qbus.datatype.Event
import org.muizenhol.qbus.sddata.SdDataStruct
import org.muizenhol.qbus.sddata.SdOutput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class DataHandler(val data: SdDataStruct) {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    var listener: (String, SdOutput) -> Unit = { _, _ -> }

    fun setEventListener(listener: (String, SdOutput) -> Unit) {
        this.listener = listener
    }

    fun getOutput(id: Int): SdOutput? {
        return data.outputs.get(id)?.clone()
    }

    /**
     * Update the internal state after a new event was received from qbus controller
     */
    private fun update(address: Byte, newData: ByteArray, event: Boolean) {
        data.outputs.filterValues { v -> v.address == address }
            .forEach { _, output ->
                val old = output.printValue()
                if (output.update(newData, event)) {
                    LOG.info(
                        "Update value for {} from {} to {}",
                        output.name,
                        old,
                        output.printValue()
                    )
                    listener.invoke(data.serialNumber, output)
                }
            }
    }

    fun update(event: AddressStatus) {
        if (event.subAddress != 0xFF.toByte()) {
            throw IllegalStateException("Can't handle this subaddress")
        }
        update(event.address, event.data, false)
    }

    fun update(event: Event) {
        update(event.address, event.data, true)
    }


}