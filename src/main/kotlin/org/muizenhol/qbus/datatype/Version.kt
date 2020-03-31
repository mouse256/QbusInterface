package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Version(cmdArray: ByteArray) : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    init {
        LOG.info("VERSION! -- {}", Common.bytesToHex(cmdArray))
        if (cmdArray.size < 14) {
            throw DataParseException("Version data to short to parse -- " + Common.bytesToHex(cmdArray))
        }
        val format = "%02d"
        LOG.info("Serial: {}{}{}", format.format(cmdArray[7]), format.format(cmdArray[8]), format.format(cmdArray[9]))
        LOG.info("Version: {}.{}", format.format(cmdArray[12]), format.format(cmdArray[13]))
    }
}