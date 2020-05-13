package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common

class PasswordVerify(val loginOk: Boolean) : DataType {
    companion object {
        //private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        operator fun invoke(cmdArray: ByteArray): PasswordVerify {
            if (cmdArray.size < 3) {
                throw DataParseException("Password verify data to short to parse -- " + Common.bytesToHex(cmdArray))
            }
            return PasswordVerify(cmdArray[2] == 0x00.toByte())
        }
    }
}