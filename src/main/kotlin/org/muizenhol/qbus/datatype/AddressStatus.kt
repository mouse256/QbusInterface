package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class AddressStatus(cmdArray: ByteArray) : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
    val data: ByteArray
    val address: Byte
    val subAddress: Byte

    init {
        LOG.info("Address status! -- {}", Common.bytesToHex(cmdArray))
        address = cmdArray[2]
        subAddress = cmdArray[3] //0xFF = all
        if (cmdArray[5] != 0x00.toByte()) {
            throw DataParseException("Invalid AddressStatus message");
        }
        val size = cmdArray[6]
        data = cmdArray.copyOfRange(8, 8 + size)

        LOG.info(
            "Data: Address: 0x{}0x{}-- {}",
            Common.byteToHex(address),
            Common.byteToHex(subAddress),
            Common.bytesToHex(data)
        )
    }
}