package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Version : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
        LOG.info("VERSION! -- {}", Common.bytesToHex(cmdArray))
        if (cmdArray.size < 14) {
            LOG.warn("Version data to short to parse -- ", Common.bytesToHex(cmdArray))
            return
        }
        val format = "%02d"
        LOG.info("Serial: {}{}{}", format.format(cmdArray[7]), format.format(cmdArray[8]), format.format(cmdArray[9]))
        LOG.info("Version: {}.{}", format.format(cmdArray[12]), format.format(cmdArray[13]))
    }
}