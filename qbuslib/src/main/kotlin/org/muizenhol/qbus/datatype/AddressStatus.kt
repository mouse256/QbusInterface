package org.muizenhol.qbus.datatype

import org.muizenhol.qbus.Common
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class AddressStatus(
    val address: Byte,
    val subAddress: Byte,
    val data: ByteArray = byteArrayOf(0x00),
    val write: Boolean = false
) : DataType {
    override val typeId = DataTypeId.ADDRESS_STATUS

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        const val SUBADDRESS_ALL = 0xFF.toByte()
        operator fun invoke(cmdArray: ByteArray): AddressStatus {
            LOG.info("Address status! -- {}", Common.bytesToHex(cmdArray))
            val write = (cmdArray[1].toInt() and 0xF0).shr(7) == 1
            val address = cmdArray[2]
            val subAddress = cmdArray[3] //0xFF = all
            if (!write && cmdArray[5] != 0x00.toByte()) {
                throw DataParseException("Invalid AddressStatus message")
            }
            val size = cmdArray[6]
            val data = cmdArray.copyOfRange(8, 8 + size)

            LOG.info(
                "Data: Address: 0x{}0x{}-- {}",
                Common.byteToHex(address),
                Common.byteToHex(subAddress),
                Common.bytesToHex(data)
            )
            return AddressStatus(address, subAddress, data, write)
        }
    }

    override fun serialize(): ByteArray {
        return serializeHeader(address, subAddress, write).plus(data)
    }
}