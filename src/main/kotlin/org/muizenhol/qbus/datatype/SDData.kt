package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.Charset

class SDData : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
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
        if (i2 == 0xFF.toByte()) {
            parseSDheader(data)
        }
    }

    private fun parseSDheader(cmdArray: ByteArray) {
        val cs = Charset.forName("windows-1252")
        val header = cmdArray.toString(cs).split("|")
        header.forEach { h ->
            LOG.info("H: {}", h)
        }
        if (header.size < 5) {
            LOG.warn("SD header too short")
            return
        }
        if (header[0] != "JSONDB") {
            LOG.warn("Corrupt SD header. Expected JSONDB, got {}", header[0])
            return
        }
        val totalSize = Integer.parseInt(header[1])
        val blockSize = Integer.parseInt(header[2])
        val dateTime = header[3]
        val fileName = header[4]
        val version = header[5]
        LOG.info(
            "SD header: size: {}, blocksize: {} time: {}, name: {}, version: {}",
            totalSize, blockSize, dateTime, fileName, version
        )
    }
}