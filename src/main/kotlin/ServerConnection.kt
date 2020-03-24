import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.lang.invoke.MethodHandles
import java.net.Socket
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


class ServerConnection(socket: Socket) : AutoCloseable {
    constructor(host: String, port: Int) : this(Socket(host, port))

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PREFIX = byteArrayOf('Q'.toByte(), 'B'.toByte(), 'U'.toByte(), 'S'.toByte(), 0, 0, 0, 0, 0)
        private const val STOP_BYTE: Byte = 0x23 //end-of-transmission
        private const val START_BYTE: Byte = 0x2A //*
        private const val BYTE_MSG: Byte = 0xFF.toByte()
        private const val BYTE_LOGIN: Byte = 0xFA.toByte()
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    }

    private val clientSocket = socket
    private val out: BufferedOutputStream = BufferedOutputStream(clientSocket.getOutputStream())
    private val inStream: BufferedInputStream = BufferedInputStream(clientSocket.getInputStream())

    /*fun sendMessage(msg: String?): String {
        out!!.println(msg)
        return `in`!!.readLine()
    }*/

    fun readWelcome() {
        val buf = ByteArray(256)
        val size = inStream.read(buf)
        LOG.info("Welcome: {}", String(buf.copyOfRange(0, size), StandardCharsets.UTF_8))
    }

    fun readData() {
        val buf = ByteArray(2048)
        if (inStream.available() == 0) {
            LOG.warn("No data available to read")
            return
        }
        val size = inStream.read(buf)
        if (size == -1) {
            LOG.warn("No data to read")
        } else {
            //LOG.info("IN: {}", bytesToHex(buf.copyOfRange(0, size)))
            parse(buf.copyOfRange(0, size))
        }
    }

    fun formatMsgAndSend(data: ByteArray, login: Boolean = false) {
        val cmdArray = ByteArray(data.size + 4)

        //first 3 bytes are to be filled in later
        //val cmdArray = byteArrayOf(0, 0, 0, START_BYTE, cmd, 0, 0, data, STOP_BYTE)
        cmdArray[0] = if (login) BYTE_LOGIN else BYTE_MSG
        cmdArray[2] = (data.size and 0x000000FF).toByte()
        cmdArray[1] = ((data.size shr 8) and 0x000000FF).toByte()
        cmdArray[cmdArray.size - 1] = STOP_BYTE
        data.copyInto(cmdArray, 3)
        LOG.info("s1: {}, s2: {}", cmdArray[1], cmdArray[2])

        LOG.info("Sending: {}{}", bytesToHex(PREFIX), bytesToHex(cmdArray))
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
        val cmd: Byte = 0x0D
        val i1: Byte = 0x0D
        val data: Byte = 0x07

        val cmdArray = byteArrayOf(START_BYTE, cmd, i1, 0, data)
        formatMsgAndSend(cmdArray)
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
        val cmd: Byte = 0x09
        val i1: Byte = 0x00
        val i2: Byte = 0x00
        val data: Byte = 0x00.toByte()

        val cmdArray = byteArrayOf(START_BYTE, cmd, i1, i2, data)
        formatMsgAndSend(cmdArray)
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
        for (i in 0..msg.size) {
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
        LOG.info("Parsing")

        for (i in 0..10) {
            val pos = getEndPosition(msg, i)
            if (pos == -1) {
                LOG.warn("No endbyte found in {}", bytesToHex(msg))
                return
            }

            //LOG.info("({}) Pos:{} size: {}", i, pos, msg.size)
            if (pos + 1 == msg.size) {
                //end and end-of-stream
                parseSingle(msg.copyOfRange(0, pos + 1))
                return
            }

            if (msg.size > pos + 10) {
                if (msg.copyOfRange(pos + 1, pos + 1 + PREFIX.size).contentEquals(PREFIX)) {
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
        LOG.info("Parsing single")
        if (msg.size < PREFIX.size) {
            LOG.warn("Msg too short, can't parse: {} -- {}", msg.size, bytesToHex(msg))
            return
        }
        if (!msg.copyOfRange(0, PREFIX.size).contentEquals(PREFIX)) {
            LOG.warn("Invalid prefix for msg: {}", bytesToHex(msg))
            return
        }
        val dataArray = msg.copyOfRange(PREFIX.size, msg.size)
        if (dataArray.size < 4) {
            LOG.warn("cmdArray too small: {}", bytesToHex(msg))
            return
        }
        when (dataArray[0]) {
            BYTE_MSG -> parseMsg(dataArray, login = false)
            BYTE_LOGIN -> parseMsg(dataArray, login = true)
            else -> LOG.warn("Unknown msg byte type: {}", bytesToHex(dataArray))
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
                LOG.warn("Corrupted msg, size mismatch: {} != {} -- {}", msg.size - 3, size, bytesToHex(msg))
                return
            }
            if (msg[msg.size - 1] != STOP_BYTE) {
                LOG.warn("Corrupted msg, last byte is not stop-byte -- {}", bytesToHex(msg))
                return
            }
            parseData(msg.copyOfRange(3, msg.size), login)
        }
    }

    private fun parseData(cmdArray: ByteArray, login: Boolean) {
        if (cmdArray[0] != START_BYTE) {
            LOG.warn("Corrupted start byte -- {}", bytesToHex(cmdArray))
            return
        }
        val type = cmdArray[1]
        if (login) {
            when (type) {
                0x00.toByte() -> parseVerifyPassword(cmdArray)
                0x02.toByte() -> parseStringData(cmdArray)
                else -> LOG.warn("unknown login msg type 0x{} -- {}", byteToHex(type), bytesToHex(cmdArray))
            }
        } else {
            when (type) {
                0x07.toByte() -> parseVersionData(cmdArray)
                0x0D.toByte() -> parseControllerOptions(cmdArray)
                0x38.toByte() -> parseAddressStatus(cmdArray)
                0x35.toByte() -> parseEvent(cmdArray)
                0x09.toByte() -> parseFatData(cmdArray)
                0x44.toByte() -> parseSDdata(cmdArray)
                else -> LOG.warn("unknown msg type 0x{} -- {}", byteToHex(type), bytesToHex(cmdArray))
            }
        }
    }

    private fun parseControllerOptions(cmdArray: ByteArray) {
        LOG.info("Controller options data")
    }

    private fun parseStringData(cmdArray: ByteArray) {
        LOG.info("STR: {}", String(cmdArray.copyOfRange(2, cmdArray.size - 1), StandardCharsets.UTF_8))
    }

    private fun parseVerifyPassword(cmdArray: ByteArray) {
        if (cmdArray.size < 3) {
            LOG.warn("Password verify data to short to parse -- ", bytesToHex(cmdArray))
            return
        }
        if (cmdArray[2] == 0x00.toByte()) {
            LOG.info("Login OK")
        } else {
            throw IllegalStateException("Login failed")
        }
    }

    fun parseVersionData(cmdArray: ByteArray) {
        LOG.info("VERSION! -- {}", bytesToHex(cmdArray))
        if (cmdArray.size < 14) {
            LOG.warn("Version data to short to parse -- ", bytesToHex(cmdArray))
            return
        }
        val format = "%02d"
        LOG.info("Serial: {}{}{}", format.format(cmdArray[7]), format.format(cmdArray[8]), format.format(cmdArray[9]))
        LOG.info("Version: {}.{}", format.format(cmdArray[12]), format.format(cmdArray[13]))
    }

    private fun parseAddressStatus(cmdArray: ByteArray) {
        LOG.info("Address status! -- {}", bytesToHex(cmdArray))
        val address = cmdArray[2]
        val subAddress = cmdArray[3] //0xFF = all
        if (cmdArray[5] != 0x00.toByte()) {
            LOG.warn("Invalid AddressStatus message");
            return
        }
        val size = cmdArray[6]
        val data = cmdArray.copyOfRange(7, 7 + size)

        LOG.info("Data: Address: 0x{}0x{}-- {}", byteToHex(address), byteToHex(subAddress), bytesToHex(data))
    }

    private fun parseEvent(cmdArray: ByteArray) {
        val address = cmdArray[2]
        val data = cmdArray.copyOfRange(3, 3 + 4)

        LOG.info("Event: Address: 0x{}-- {}", byteToHex(address), bytesToHex(data))
    }

    private fun parseFatData(cmdArray: ByteArray) {
        val i1 = cmdArray[2]
        val i2 = cmdArray[3]
        val length = cmdArray[4]
        if (cmdArray[5] != 0x00.toByte()) {
            LOG.error("Got error in FAT data")
            return
        }

        LOG.info("FAT data: i1: 0x{}, i2:0x{}, length:{}", byteToHex(i1), byteToHex(i2), length)
    }

    private fun parseSDdata(cmdArray: ByteArray) {
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
            byteToHex(i1), byteToHex(i2), byteToHex(num), cmdArray.size, index
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


    private fun byteToHex(byte: Byte): String {
        return bytesToHex(byteArrayOf(byte))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 3)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 3] = HEX_ARRAY[v ushr 4]
            hexChars[j * 3 + 1] = HEX_ARRAY[v and 0x0F]
            hexChars[j * 3 + 2] = ' '
        }
        return String(hexChars)
    }

    override fun close() {
        out.close()
        inStream.close()
        clientSocket.close()
    }
}