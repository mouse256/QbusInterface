package org.muizenhol.qbus

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.muizenhol.qbus.datatype.*
import org.muizenhol.qbus.sddata.SdDataParser
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Controller private constructor(
    private val username: String,
    private val password: String,
    private val serial: String
) : ServerConnection.Listener, AutoCloseable {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    private lateinit var conn: ServerConnection
    private var state = State.INIT
    private var downloader: Downloader? = null
    private var stateFetcher: StateFetcher? = null
    private var stateChangeListener: ((State) -> Unit)? = null

    val job = CompletableDeferred<Unit>()
    var data: SdDataStruct? = null


    constructor(serial: String, username: String, password: String, host: String, port: Int = 8446) :
            this(username, password, serial) {
        conn = ServerConnection(host, port, this)
    }

    constructor(serial: String, username: String, password: String, connection: ServerConnection) :
            this(username, password, serial) {
        conn = connection
    }

    override fun onEvent(event: DataType) {
        //event
        try {
            when (event) {
                is Version -> {
                    if (state == State.WAIT_FOR_VERSION) {
                        if (!event.serial.equals(serial)) {
                            LOG.error("Invalid serial")
                            job.completeExceptionally(
                                RuntimeException(
                                    "Invalid serial, got " + event.serial + " expected " + serial
                                )
                            )
                            return
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
                            job.completeExceptionally(RuntimeException("Login error"))
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
                    } else {
                        LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                    }
                }
                is Event -> {
                    if (state == State.READY) {
                        data!!.update(event)
                    } else {
                        LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                    }
                }
                else -> {
                    LOG.warn("Don't know what to do with event {} in state {}", event.javaClass, state)
                }
            }
        } catch (ex: Exception) {
            job.completeExceptionally(ex)
        }
    }

    override fun onParseException(ex: DataParseException) {
        job.completeExceptionally(ex)
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
                data = parser.parse()
                stateFetcher = StateFetcher()
                stateFetcher!!.next()
            }
        }

        fun addBlock(data: SDData.SDDataBlock) {
            parser.addData(data.getData())
            nextBlock()
        }
    }

    private inner class StateFetcher() {
        var currentItem = 0
        val addressList: List<Byte>

        init {
            addressList = data!!.outputs.map { out -> out.value.address }.distinct()
            updateState(State.FETCH_STATE)
        }

        fun next() {
            if (currentItem >= addressList.size) {
                updateState(State.READY)
                return
            }
            val previous = currentItem
            currentItem++
            conn.writeGetAddressStatus(addressList[previous])
        }

        fun add(state: AddressStatus) {
            data!!.update(state)
            next()
        }

    }

    private fun updateState(newState: State) {
        LOG.info("Updating state {} -> {}", state, newState)
        state = newState
        stateChangeListener?.invoke(newState)
    }

    override fun close() {
        conn.close()
        job.complete(Unit)
    }

    fun run(stateChangeListener: ((State) -> Unit)? = null): CompletableDeferred<Unit> {
        this.stateChangeListener = stateChangeListener
        conn.startDataReader()
        conn.readWelcome()
           Thread.sleep(1000)
        updateState(State.WAIT_FOR_VERSION)
        conn.writeMsgVersion()
        return job
    }
}

