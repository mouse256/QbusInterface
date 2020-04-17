package org.muizenhol.qbus

import org.muizenhol.qbus.datatype.*
import org.muizenhol.qbus.sddata.SdDataParser
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Controller(
    private val username: String,
    private val password: String,
    private val host: String,
    private val port: Int = 8446
) : ServerConnection.Listener {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    private var state = State.INIT
    private val conn: ServerConnection = ServerConnection(host, port, this)
    private var running = true
    private var downloader: Downloader? = null


    override fun onEvent(event: DataType) {
        //event
        when (event) {
            is Version -> {
                if (state == State.WAIT_FOR_VERSION) {
                    updateState(State.LOGIN)
                    conn.login(username, password)
                } else {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
            is PasswordVerify -> {
                if (state == State.LOGIN) {
                    if (event.loginOk) {
                        LOG.info("Login OK")
                        updateState(State.WAIT_FOR_CONTROLLER_OPTIONS)
                        conn.writeControllerOptions()
                    } else {
                        LOG.error("Login error")
                        running = false
                    }
                } else {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
            is ControllerOptions -> {
                if (state == State.WAIT_FOR_CONTROLLER_OPTIONS) {
                    updateState(State.WAIT_FOR_FAT_DATA)
                    conn.writegetFATData()
                } else {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
            is FatData -> {
                if (state == State.WAIT_FOR_FAT_DATA) {
                    updateState(State.DOWNLOAD_SD_DATA_HEADER)
                    conn.writegetSDData() //header
                } else {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
            is SDData.SDDataHeader -> {
                if (state == State.DOWNLOAD_SD_DATA_HEADER) {
                    downloader = Downloader(event)
                    updateState(State.DOWNLOAD_SD_DATA)
                    downloader?.nextBlock()
                } else {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
            is SDData.SDDataBlock -> {
                if (state == State.DOWNLOAD_SD_DATA) {
                    downloader?.addBlock(event)
                } else {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
            else -> {
                LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
            }
        }
    }

    override fun onParseException(ex: DataParseException) {
        //Exception
    }

    enum class State {
        INIT,
        WAIT_FOR_VERSION,
        WAIT_FOR_CONTROLLER_OPTIONS,
        WAIT_FOR_FAT_DATA,
        DOWNLOAD_SD_DATA_HEADER,
        DOWNLOAD_SD_DATA,
        FETCH_STATE,
        LOGIN
    }

    private inner class Downloader(val header: SDData.SDDataHeader) {
        var currentBlock = 0
        val parser = SdDataParser()
        var parsedData: SdDataStruct? = null

        fun nextBlock() {
            currentBlock++
            if (currentBlock <= header.numBlocks) {
                conn.writegetSDData(currentBlock)
            } else {
                parsedData = parser.parse()
                updateState(State.FETCH_STATE)
            }
        }

        fun addBlock(data: SDData.SDDataBlock) {
            parser.addData(data.getData())
            nextBlock()
        }
    }

    private fun updateState(newState: State) {
        LOG.info("Updating state {} -> {}", state, newState)
        state = newState
    }

    fun run() {

        conn.use { conn ->
            conn.startDataReader()
            conn.readWelcome()
            Thread.sleep(1000)
            updateState(State.WAIT_FOR_VERSION)
            conn.writeMsgVersion()

            while (running) {
                Thread.sleep(1_000)
            }
        }
    }
}

