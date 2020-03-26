package org.muizenhol.qbus.datatype

interface DataType {
    fun parse(cmdArray: ByteArray)
}