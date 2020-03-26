package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class AddressStatus : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
        LOG.info("Address status! -- {}", Common.bytesToHex(cmdArray))
        val address = cmdArray[2]
        val subAddress = cmdArray[3] //0xFF = all
        if (cmdArray[5] != 0x00.toByte()) {
            LOG.warn("Invalid AddressStatus message");
            return
        }
        val size = cmdArray[6]
        val data = cmdArray.copyOfRange(7, 7 + size)

        LOG.info(
            "Data: Address: 0x{}0x{}-- {}",
            Common.byteToHex(address),
            Common.byteToHex(subAddress),
            Common.bytesToHex(data)
        )
    }
}