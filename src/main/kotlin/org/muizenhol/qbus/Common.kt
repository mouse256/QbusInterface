package org.muizenhol.qbus


class Common {
    companion object {
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

        fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 3)
            for (j in bytes.indices) {
                val v: Int = bytes[j].toInt() and 0xFF
                hexChars[j * 3] = HEX_ARRAY[v ushr 4]
                hexChars[j * 3 + 1] = HEX_ARRAY[v and 0x0F]
                hexChars[j * 3 + 2] = ' '
            }
            return String(hexChars)
        }

        fun byteToHex(byte: Byte): String {
            return bytesToHex(byteArrayOf(byte))
        }
    }
}

