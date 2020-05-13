package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Version(val version: String, val serial: String) : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        operator fun invoke(cmdArray: ByteArray): Version {
            LOG.info("VERSION! -- {}", Common.bytesToHex(cmdArray))
            if (cmdArray.size < 14) {
                throw DataParseException("Version data to short to parse -- " + Common.bytesToHex(cmdArray))
            }
            val format = "%02d"
            val serial = format.format(cmdArray[7]) + format.format(cmdArray[8]) + format.format(cmdArray[9])
            val version = format.format(cmdArray[12]) + "." + format.format(cmdArray[13])
            LOG.info("Serial: {}", serial)
            LOG.info("Version: {}", version)
            return Version(version, serial)
        }
    }

}