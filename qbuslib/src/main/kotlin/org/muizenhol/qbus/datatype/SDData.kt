package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.muizenhol.qbus.ServerConnection
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.Charset

sealed class SDData : DataType {
    override val typeId = DataTypeId.SD_DATA

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun parse(cmdArray: ByteArray): SDData {
            val i1 = cmdArray[2]
            val i2 = cmdArray[3]
            val num = cmdArray[4]
            var index = 4
            if (cmdArray[5] == 0x00.toByte() && cmdArray[6] == num) {
                index = 6
            }
            val data = cmdArray.copyOfRange(index + 1, cmdArray.size - 1)

            LOG.info(
                "SD: i1: 0x{}i2: 0x{}num: 0x{}size:{} index: {}",
                Common.byteToHex(i1), Common.byteToHex(i2), Common.byteToHex(num), cmdArray.size, index
            )
            return when (i2) {
                0xFF.toByte() -> SDDataHeader(data)
                else -> SDDataBlock(0xFF.toByte() - i2, data)
            }
        }
    }

    class SDDataHeader(totalSize: Int, blockSize: Int) : SDData() {

        val numBlocks: Int = kotlin.math.ceil(totalSize.toDouble() / blockSize.toDouble()).toInt()

        companion object {
            operator fun invoke(cmdArray: ByteArray): SDDataHeader {
                val cs = Charset.forName("windows-1252")
                val header = cmdArray.toString(cs).split("|")
                header.forEach { h ->
                    LOG.info("H: {}", h)
                }
                if (header.size < 5) {
                    throw DataParseException("SD header too short")
                }
                if (header[0] != "JSONDB") {
                    throw DataParseException("Corrupt SD header. Expected JSONDB, got " + header[0])
                }
                val totalSize = Integer.parseInt(header[1])
                val blockSize = Integer.parseInt(header[2])

                val dateTime = header[3]
                val fileName = header[4]
                val version = header[5]
                LOG.info(
                    "SD header: size: {}, blocksize: {}, time: {}, name: {}, version: {}",
                    totalSize, blockSize, dateTime, fileName, version
                )
                return SDDataHeader(totalSize, blockSize)
            }
        }
    }

    class SDDataBlock(val nr: Int, private val data: ByteArray) : SDData() {
        fun getData(): ByteArray {
            return data
        }
    }
}