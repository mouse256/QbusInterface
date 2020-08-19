@file:Suppress("unused")

package org.muizenhol.qbus

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muizenhol.qbus.datatype.*
import org.muizenhol.qbus.exception.InvalidSerialException
import org.muizenhol.qbus.exception.LoginException
import org.muizenhol.qbus.exception.QbusException
import org.muizenhol.qbus.sddata.SdDataParser
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.time.Duration

class Controller(
    private val serial: String,
    private val username: String,
    private val password: String,
    private val host: String,
    private val exceptionHandler: (QbusException) -> Unit,
    private val stateChangeHandler: ((State) -> Unit) = {},
    private val onReady: (DataHandler) -> Unit = {},
    private val port: Int = 8446,
    private val connectionCreator: (String, Int, ServerConnection.Listener) -> ServerConnection = serverConnectionCreator,
    private val reconnectTimeout: Duration = Duration.ofMinutes(1),
    private val sleepOnStart: Duration = Duration.ofSeconds(1)
) : ServerConnection.Listener, AutoCloseable {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val serverConnectionCreator: (host: String, port: Int, listener: ServerConnection.Listener) -> ServerConnection =
            { host, port, listener ->
                ServerConnection(host, port, listener)
            }
    }

    private var conn: ServerConnection = connectionCreator.invoke(host, port, this)
    private var state = State.INIT
    private var downloader: Downloader? = null
    private var stateFetcher: StateFetcher? = null
    var dataHandler: DataHandler? = null

    override fun onConnectionClosed() {
        LOG.info("Restarting connection as connection close is detected")
        restart()
    }

    override fun onEvent(event: DataType) {
        //event
        try {
            when (event) {
                is Version -> {
                    if (state == State.WAIT_FOR_VERSION) {
                        if (!event.serial.equals(serial)) {
                            LOG.error("Invalid serial")
                            exceptionHandler.invoke(
                                InvalidSerialException(
                                    "Invalid serial, got " + event.serial + " expected " + serial
                                )
                            )
                        }
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
                            exceptionHandler.invoke(LoginException("Login error"))
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
                is AddressStatus -> {
                    if (state == State.FETCH_STATE) {
                        stateFetcher!!.add(event)
                    } else if (state == State.READY) {
                        LOG.debug("Ignoring AddressStatus in Ready state.")// also sent as event.
                    } else {
                        LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                    }
                }
                is Event -> {
                    if (state == State.READY) {
                        dataHandler!!.update(event)
                    } else {
                        LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                    }
                }
                is Relogin -> {
                    restart()
                }
                else -> {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
        } catch (ex: Exception) {
            LOG.warn("Exception in parsing data", ex)
        }
    }

    override fun onParseException(ex: DataParseException) {
        LOG.warn("Parse exception", ex)
    }

    enum class State {
        INIT,
        WAIT_FOR_VERSION,
        WAIT_FOR_CONTROLLER_OPTIONS,
        WAIT_FOR_FAT_DATA,
        DOWNLOAD_SD_DATA_HEADER,
        DOWNLOAD_SD_DATA,
        FETCH_STATE,
        LOGIN,
        READY
    }

    private inner class Downloader(val header: SDData.SDDataHeader) {
        var currentBlock = 0
        val parser = SdDataParser()

        fun nextBlock() {
            currentBlock++
            if (currentBlock <= header.numBlocks) {
                LOG.info("Downloading SD block {}", currentBlock)
                conn.writegetSDData(currentBlock)
            } else {
                LOG.info("READY???")
                parser.parse()?.let { d ->
                    LOG.info("READY!!!")
                    dataHandler = DataHandler(d)
                    onReady(dataHandler!!)
                }
                stateFetcher = StateFetcher()
                stateFetcher!!.start()
            }
        }

        fun addBlock(data: SDData.SDDataBlock) {
            parser.addData(data.getData())
            nextBlock()
        }
    }

    private inner class StateFetcher {
        var currentItem = 0
        val addressList: List<Byte>

        init {
            addressList = dataHandler!!.data.outputs.map { out -> out.value.address }.distinct()
        }

        private fun next() {
            if (currentItem >= addressList.size) {
                updateState(State.READY)
                return
            }
            val previous = currentItem
            currentItem++
            conn.writeGetAddressStatus(addressList[previous])
        }

        fun add(state: AddressStatus) {
            dataHandler!!.update(state)
            next()
        }

        fun start() {
            currentItem = 0
            updateState(State.FETCH_STATE)
            next()
        }

    }

    private fun updateState(newState: State) {
        LOG.info("Updating state {} -> {}", state, newState)
        state = newState
        stateChangeHandler.invoke(newState)
    }

    fun setNewState(output: SdDataStruct.Output) {
        LOG.info("sending event to Qbus: {} ({}: {}) -> {}", output.type, output.id, output.name, output.value)
        output.value?.let { value ->
            when (output.type) {
                SdDataStruct.Type.ON_OFF -> {
                    val addressStatus = AddressStatus(
                        output.address,
                        output.subAddress,
                        data = byteArrayOf(0x00, value),
                        write = true
                    )
                    conn.write(addressStatus)
                }
                else -> LOG.warn("Don't know how to handle type " + output.type)
            }
        }
    }

    private fun restart() {
        LOG.info("Restarting login in {}", reconnectTimeout)
        try {
            //try to close again, just in case not all resources were cleaned
            conn.close()
        } catch (e: Exception) {
            //Don't care
        }
        val ctrl = this
        GlobalScope.launch {
            delay(reconnectTimeout.toMillis())
            LOG.info("Restarting login now")
            conn = connectionCreator.invoke(host, port, ctrl)
            start()
        }
    }

    override fun close() {
        conn.close()
    }

    fun start() {
        LOG.info("Start")
        conn.startDataReader()
        conn.readWelcome()
        Thread.sleep(sleepOnStart.toMillis())
        updateState(State.WAIT_FOR_VERSION)
        conn.writeMsgVersion()
    }

}

