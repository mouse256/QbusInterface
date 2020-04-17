package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common

class PasswordVerify(cmdArray: ByteArray) : DataType {
    companion object {
        //private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    val loginOk: Boolean

    init {
        if (cmdArray.size < 3) {
            throw DataParseException("Password verify data to short to parse -- " + Common.bytesToHex(cmdArray))
        }
        loginOk = cmdArray[2] == 0x00.toByte()
    }
}