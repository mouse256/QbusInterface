package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.ServerConnection

interface DataType {
    val typeId: DataTypeId

    fun serializeHeader(i1: Byte, i2: Byte, write: Boolean = false): ByteArray {
        val id: Byte = if (write) (typeId.id + 0x80.toByte()).toByte() else typeId.id //add 128
        return byteArrayOf(ServerConnection.START_BYTE, id, i1, i2)
    }

    fun serialize(): ByteArray {
        throw UnsupportedOperationException("Not supported for datatype " + typeId)
    }
}

enum class DataTypeId(val id: Byte) {
    PASSWORD_VERIFY(0x00.toByte()),
    STRING_DATA(0x02.toByte()),
    VERSION(0x07.toByte()),
    FAT_DATA(0x09.toByte()),
    CONTROLLER_OPTIONS(0x0D.toByte()),
    EVENT(0x35.toByte()),
    ADDRESS_STATUS(0x38.toByte()),
    SD_DATA(0x44.toByte()),
    RELOGIN(0x7F.toByte()),
}

class DataParseException(msg: String) : Exception(msg) {
}
