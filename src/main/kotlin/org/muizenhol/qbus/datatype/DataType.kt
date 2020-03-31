package org.muizenhol.qbus.datatype

import java.lang.Exception

interface DataType {
    //fun parse(cmdArray: ByteArray)
}

class DataParseException(msg: String): Exception(msg) {
}
