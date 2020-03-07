import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.lang.invoke.MethodHandles
import java.net.Socket
import java.nio.charset.StandardCharsets


class ServerConnection(host: String, port: Int) : AutoCloseable {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val PREFIX = byteArrayOf('Q'.toByte(), 'B'.toByte(), 'U'.toByte(), 'S'.toByte(), 0, 0, 0, 0, 0)
        private const val STOP_BYTE: Byte = 0x23 //end-of-transmission
        private const val START_BYTE: Byte = 0x2A //*
        private const val BYTE_MSG: Byte = 0xFF.toByte()
        private const val BYTE_LOGIN: Byte = 0xFA.toByte()
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    }

    private val clientSocket: Socket = Socket(host, port)
    private val out: BufferedOutputStream
    private val inStream: BufferedInputStream

    init {
        out = BufferedOutputStream(clientSocket.getOutputStream())
        inStream = BufferedInputStream(clientSocket.getInputStream())
    }

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
        val buf = ByteArray(256)
        val size = inStream.read(buf)
        if (size == -1) {
            LOG.warn("No data to read")
        } else {
            //LOG.info("IN: {}", bytesToHex(buf.copyOfRange(0, size)))
            parse(buf.copyOfRange(0, size))
        }
    }

    fun writeMsgVersion() {
        val cmd: Byte = 0x07
        val data: Byte = 0x04

        //first 3 bytes are to be filled in later
        val cmdArray = byteArrayOf(0, 0, 0, START_BYTE, cmd, 0, 0, data, STOP_BYTE)
        cmdArray[0] = BYTE_MSG //if login, use 0xFA
        val size = cmdArray.size - 4 //don't count first 3 bytes and stop_byte for the size
        cmdArray[2] = (size and 0x000000FF).toByte()
        cmdArray[1] = ((size shr 8) and 0x000000FF).toByte()
        LOG.info("s1: {}, s2: {}", cmdArray[1], cmdArray[2])

        LOG.info("Sending: {}{}", bytesToHex(PREFIX), bytesToHex(cmdArray))
        out.write(PREFIX)
        out.write(cmdArray)
        out.flush()
    }

    fun parse(msg: ByteArray) {
        LOG.info("Parsing")
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
            BYTE_MSG -> parseMsg(dataArray)
            BYTE_LOGIN -> LOG.warn("Login msg parsing not yet supported")
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

    fun parseMsg(msg: ByteArray) {
        getSize(msg)?.let { size ->
            if (msg.size - 3 != size) {
                LOG.warn("Corrupted msg, size mismatch: {} != {} -- {}", msg.size - 3, size, bytesToHex(msg))
                return
            }
            if (msg[msg.size - 1] != STOP_BYTE) {
                LOG.warn("Corrupted msg, last byte is not stop-byte -- {}", bytesToHex(msg))
                return
            }
            parseData(msg.copyOfRange(3, msg.size))
        }
    }

    fun parseData(cmdArray: ByteArray) {
        if (cmdArray[0] != START_BYTE) {
            LOG.warn("Corrupted start byte -- {}", bytesToHex(cmdArray))
            return
        }
        when (cmdArray[1]) {
            0x07.toByte() -> parseVersionData(cmdArray)
            else -> LOG.warn("unknown msg type -- {}", bytesToHex(cmdArray))
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

    fun bytesToHex(bytes: ByteArray): String {
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