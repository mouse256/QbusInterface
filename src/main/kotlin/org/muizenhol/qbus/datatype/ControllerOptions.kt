package org.muizenhol.qbus.datatype

import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class ControllerOptions(/*cmdArray: ByteArray*/) : DataType {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    init {
        LOG.info("Controller options data")
    }
}