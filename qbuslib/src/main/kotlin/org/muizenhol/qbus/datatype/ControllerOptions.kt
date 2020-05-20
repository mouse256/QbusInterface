package org.muizenhol.qbus.datatype

import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class ControllerOptions(val i1: Byte, val data: ByteArray) : DataType {
    override val typeId = DataTypeId.CONTROLLER_OPTIONS

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    init {
        LOG.info("Controller options data")
    }

    override fun serialize(): ByteArray {
        return serializeHeader(i1, 0x00).plus(data)
    }
}