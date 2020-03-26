package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class FatData : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
        val i1 = cmdArray[2]
        val i2 = cmdArray[3]
        val length = cmdArray[4]
        if (cmdArray[5] != 0x00.toByte()) {
            LOG.error("Got error in FAT data")
            return
        }

        LOG.info("FAT data: i1: 0x{}, i2:0x{}, length:{}", Common.byteToHex(i1), Common.byteToHex(i2), length)
    }
}