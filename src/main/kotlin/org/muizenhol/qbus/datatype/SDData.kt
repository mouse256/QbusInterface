package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.Charset

sealed class SDData : DataType {
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

    class SDDataHeader(cmdArray: ByteArray) : SDData() {
        val totalSize: Int
        val blockSize: Int
        val numBlocks:Int

        init {
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
            totalSize = Integer.parseInt(header[1])
            blockSize = Integer.parseInt(header[2])
            numBlocks = Math.ceil(totalSize.toDouble() / blockSize.toDouble()).toInt()
            val dateTime = header[3]
            val fileName = header[4]
            val version = header[5]
            LOG.info(
                "SD header: size: {}, blocksize: {}, {} blocks, time: {}, name: {}, version: {}",
                totalSize, blockSize, numBlocks, dateTime, fileName, version
            )
        }


    }

    class SDDataBlock(val id: Int, private val data: ByteArray) : SDData() {
        fun getData(): ByteArray {
            return data
        }
    }
}