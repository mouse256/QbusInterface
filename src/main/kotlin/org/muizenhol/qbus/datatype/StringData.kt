package org.muizenhol.qbus.datatype

import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.charset.StandardCharsets

class StringData : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
        LOG.info("STR: {}", String(cmdArray.copyOfRange(2, cmdArray.size - 1), StandardCharsets.UTF_8))
    }

}