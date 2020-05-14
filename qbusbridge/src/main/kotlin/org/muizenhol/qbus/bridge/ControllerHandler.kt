package org.muizenhol.qbus.bridge

import io.quarkus.runtime.Startup
import org.muizenhol.qbus.Controller
import org.muizenhol.qbus.DataHandler
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.invoke.MethodHandles
import java.util.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped

@Startup
@ApplicationScoped
class ControllerHandler {
    var dataHandler: DataHandler? = null
    lateinit var controller: Controller

    @PostConstruct
    fun create() {
        LOG.info("creating vertx")
        /*val vertx: Vertx = Vertx.vertx()
        webClient = WebClient.create(vertx)
        vertx.setPeriodic(Duration.ofMinutes(30).toMillis(), { l -> fetchRss() })
        fetchRss() //setPeriod does not fire immediately, do so.*/

        val prop = Properties()
        val file = File("/tmp/qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val username = getOrThrow(prop, "username")
        val password = getOrThrow(prop, "password")
        val serial = getOrThrow(prop, "serial")
        val host = getOrThrow(prop, "host")
        controller = Controller(serial, username, password, host, { ready ->
            LOG.info("XXXXXXXXx ready")
            dataHandler = ready
            ready.setEventListener(this::onDataUpdate)
        })
        controller.run()
    }

    @PreDestroy
    fun destroy() {
        LOG.info("Destroying")
        controller.close()
    }

    private fun getOrThrow(prop: Properties, name: String): String {
        return prop.getProperty(name) ?: throw IllegalArgumentException("Can't find property for $name")
    }

    private fun onDataUpdate(data: SdDataStruct.Output) {
        LOG.info("update for {} to {}", data.name, data.value)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}