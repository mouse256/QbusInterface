package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Event(val address: Byte, val data: ByteArray) : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        operator fun invoke(cmdArray: ByteArray): Event {
            val address = cmdArray[2]
            val data = cmdArray.copyOfRange(3, 3 + 4)

            LOG.info("Event: Address: 0x{}-- {}", Common.byteToHex(address), Common.bytesToHex(data))
            return Event(address, data)
        }
    }
}