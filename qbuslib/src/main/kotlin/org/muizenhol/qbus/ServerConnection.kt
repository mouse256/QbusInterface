package org.muizenhol.qbus

import kotlinx.coroutines.*
import org.muizenhol.qbus.datatype.*
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.net.Socket
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.experimental.and


class ServerConnection(socket: Socket, private val listener: Listener) : AutoCloseable {
    constructor(host: String, port: Int, listener: Listener) : this(Socket(host, port), listener)

    interface Listener {
        fun onEvent(event: DataType)
        fun onParseException(ex: DataParseException)
        fun onConnectionClosed()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PREFIX = byteArrayOf('Q'.toByte(), 'B'.toByte(), 'U'.toByte(), 'S'.toByte(), 0, 0, 0, 0, 0)
        private const val STOP_BYTE: Byte = 0x23 //end-of-transmission
        const val START_BYTE: Byte = 0x2A //*
        private const val BYTE_MSG: Byte = 0xFF.toByte()
        private const val BYTE_LOGIN: Byte = 0xFA.toByte()
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    }

    private val clientSocket = socket
    private val out: BufferedOutputStream = BufferedOutputStream(clientSocket.getOutputStream())
    private val inStream: BufferedInputStream = BufferedInputStream(clientSocket.getInputStream())
    private var running = true
    private var bgThread: Job? = null

    fun readWelcome() {
        val buf = ByteArray(256)
        val size = inStream.read(buf)
        LOG.info("Welcome: {}", String(buf.copyOfRange(0, size), StandardCharsets.UTF_8))
    }

    fun startDataReader() {
        LOG.info("Starting datareader")
        bgThread = GlobalScope.launch {
            try {
                while (running) {
                    if (!readData()) {
                        LOG.warn("Stream closed!")
                        running = false
                    }
                }
            } catch (e: IOException) {
                LOG.warn("IO exception", e)
                listener.onConnectionClosed()
            }
        }
    }

    fun readData(): Boolean {
        val buf = ByteArray(2048)
        val size = inStream.read(buf)
        if (size == -1) {
            LOG.warn("No data to read")
            return false
        } else {
            //LOG.info("IN: {}", bytesToHex(buf.copyOfRange(0, size)))
            parse(buf.copyOfRange(0, size))
            return true
        }
    }

    private fun formatMsgAndSend(data: ByteArray, login: Boolean = false) {
        val cmdArray = ByteArray(data.size + 4)

        //first 3 bytes are to be filled in later
        //val cmdArray = byteArrayOf(0, 0, 0, START_BYTE, cmd, 0, 0, data, STOP_BYTE)
        cmdArray[0] = if (login) BYTE_LOGIN else BYTE_MSG
        cmdArray[2] = (data.size and 0x000000FF).toByte()
        cmdArray[1] = ((data.size shr 8) and 0x000000FF).toByte()
        cmdArray[cmdArray.size - 1] = STOP_BYTE
        data.copyInto(cmdArray, 3)
        LOG.trace("s1: {}, s2: {}", cmdArray[1], cmdArray[2])

        LOG.info(
            "Sending: {}{}", Common.bytesToHex(
                PREFIX
            ), Common.bytesToHex(cmdArray)
        )
        out.write(PREFIX)
        out.write(cmdArray)
        out.flush()
    }

    fun writeMsgVersion() {
        val cmd: Byte = 0x07
        val data: Byte = 0x04

        val cmdArray = byteArrayOf(START_BYTE, cmd, 0, 0, data)
        formatMsgAndSend(cmdArray)
    }

    fun writeControllerOptions() {
        write(ControllerOptions(0x0D, byteArrayOf(0x07)))
    }

    fun writeGetAddressStatus(address: Byte) {
        write(AddressStatus(address, AddressStatus.SUBADDRESS_ALL))
    }

    fun write(data: DataType) {
        formatMsgAndSend(data.serialize())
    }

    fun writegetSDData(part: Int = 0) {
        val cmd: Byte = 0x44
        val i1: Byte = 0x29
        var i2: Byte = 0xFF.toByte()
        for (i in 1..part) {
            i2 = i2.dec()
        }
        val data: Byte = 0xEF.toByte()

        val cmdArray = byteArrayOf(START_BYTE, cmd, i1, i2, data)
        formatMsgAndSend(cmdArray)
    }

    fun writegetFATData() {
        formatMsgAndSend(FatData().serialize())
    }

    fun login(username: String, password: String) {
        val cmdArray = ByteArray(34)
        cmdArray[0] = START_BYTE
        cmdArray[1] = 0x00
        val cs = Charset.forName("windows-1252")

        val user2 = username.substring(0, minOf(16, username.length))
        val pass2 = password.substring(0, minOf(16, password.length))
        user2.toByteArray(cs).copyInto(cmdArray, 2)
        pass2.toByteArray(cs).copyInto(cmdArray, 2 + 16)
        formatMsgAndSend(cmdArray, login = true)
        //cmdArray[cmdArray.size - 1] = STOP_BYTE
    }

    private fun getEndPosition(msg: ByteArray, num: Int): Int {
        var found = 0;
        for (i in msg.indices) {
            if (msg[i] == STOP_BYTE) {
                if (found == num) {
                    return i
                }
                found++
            }
        }
        return -1
    }

    fun parse(msg: ByteArray) {
        LOG.trace("Parsing")

        for (i in 0..10) {
            val pos = getEndPosition(msg, i)
            if (pos == -1) {
                LOG.warn("No endbyte found in {}", Common.bytesToHex(msg))
                return
            }

            //LOG.info("({}) Pos:{} size: {}", i, pos, msg.size)
            if (pos + 1 == msg.size) {
                //end and end-of-stream
                parseSingle(msg.copyOfRange(0, pos + 1))
                return
            }

            if (msg.size > pos + 10) {
                if (msg.copyOfRange(pos + 1, pos + 1 + PREFIX.size).contentEquals(
                        PREFIX
                    )
                ) {
                    //2 or more messages in the same buffer
                    //parsing first
                    parseSingle(msg.copyOfRange(0, pos + 1))

                    //and looping for the next one
                    parse(msg.copyOfRange(pos + 1, msg.size))
                    return
                }
            }
            //if nothing matches, search for the next occurence of the stopbyte
        }
    }

    private fun parseSingle(msg: ByteArray) {
        LOG.trace("Parsing single")
        if (msg.size < PREFIX.size) {
            LOG.warn("Msg too short, can't parse: {} -- {}", msg.size, Common.bytesToHex(msg))
            return
        }
        if (!msg.copyOfRange(0, PREFIX.size).contentEquals(
                PREFIX
            )
        ) {
            LOG.warn("Invalid prefix for msg: {}", Common.bytesToHex(msg))
            return
        }
        val dataArray = msg.copyOfRange(PREFIX.size, msg.size)
        if (dataArray.size < 4) {
            LOG.warn("cmdArray too small: {}", Common.bytesToHex(msg))
            return
        }
        when (dataArray[0]) {
            BYTE_MSG -> parseMsg(dataArray, login = false)
            BYTE_LOGIN -> parseMsg(dataArray, login = true)
            else -> LOG.warn("Unknown msg byte type: {}", Common.bytesToHex(dataArray))
        }
    }

    fun getSize(msg: ByteArray): Int? {
        if (msg.size < 3) {
            LOG.warn("Msg too small to read size")
            return null
        }
        return ((msg[1].toInt() and 0xFF) shl 8) + (msg[2].toInt() and 0xFF)
    }

    private fun parseMsg(msg: ByteArray, login: Boolean) {
        getSize(msg)?.let { size ->
            if (msg.size - 3 != size) {
                LOG.warn("Corrupted msg, size mismatch: {} != {} -- {}", msg.size - 3, size, Common.bytesToHex(msg))
                return
            }
            if (msg[msg.size - 1] != STOP_BYTE) {
                LOG.warn("Corrupted msg, last byte is not stop-byte -- {}", Common.bytesToHex(msg))
                return
            }
            parseData(msg.copyOfRange(3, msg.size), login)
        }
    }

    private fun parseData(cmdArray: ByteArray, login: Boolean) {
        if (cmdArray[0] != START_BYTE) {
            LOG.warn("Corrupted start byte -- {}", Common.bytesToHex(cmdArray))
            return
        }

        //val type = if (cmdArray[1].toInt()< 128) cmdArray[1] else (cmdArray[1].toInt() - 128).toByte()
        val type = cmdArray[1] and 0x7F.toByte()

        try {
            val dataType: DataType? = when (login) {
                true -> when (type) {
                    DataTypeId.PASSWORD_VERIFY.id -> PasswordVerify(cmdArray)
                    DataTypeId.STRING_DATA.id -> StringData(cmdArray)
                    DataTypeId.RELOGIN.id -> Relogin()
                    else -> throw DataParseException(
                        "unknown loginmsg type 0x" + Common.byteToHex(type) + " -- "
                                + Common.bytesToHex(cmdArray)
                    )
                }
                false -> when (type) {
                    DataTypeId.VERSION.id -> Version(cmdArray)
                    DataTypeId.CONTROLLER_OPTIONS.id -> ControllerOptions(0x00, byteArrayOf())
                    DataTypeId.ADDRESS_STATUS.id -> AddressStatus(cmdArray)
                    DataTypeId.EVENT.id -> Event(cmdArray)
                    DataTypeId.FAT_DATA.id -> FatData(cmdArray)
                    DataTypeId.SD_DATA.id -> SDData.parse(cmdArray)
                    else -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Ingoring unknown msg type 0x{} -- {}",Common.byteToHex(type),
                                     Common.bytesToHex(cmdArray))
                        } else {
                            LOG.info("Ingoring unknown msg type 0x{}",Common.byteToHex(type))
                        }
                        null
                    }
                }
            }
            dataType?.run {
                LOG.info("Parsed type: {}", dataType.typeId)
                listener.onEvent(dataType)
            }
        } catch (ex: DataParseException) {
            LOG.warn("Parsing error")
            listener.onParseException(ex)
        }
    }

    override fun close() {
        out.close()
        inStream.close()
        clientSocket.close()
        running = false
    }

    fun triggerReadError() {
        inStream.close()
    }
}