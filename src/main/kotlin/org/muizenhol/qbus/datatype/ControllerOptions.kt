package org.muizenhol.qbus.datatype

import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class ControllerOptions : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    override fun parse(cmdArray: ByteArray) {
        LOG.info("Controller options data")
    }
}