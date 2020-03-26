package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class PasswordVerify : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
        if (cmdArray.size < 3) {
            LOG.warn("Password verify data to short to parse -- ", Common.bytesToHex(cmdArray))
            return
        }
        if (cmdArray[2] == 0x00.toByte()) {
            LOG.info("Login OK")
        } else {
            throw IllegalStateException("Login failed")
        }
    }
}