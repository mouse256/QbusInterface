package org.muizenhol.qbus

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.invoke.MethodHandles
import java.util.*

class Main {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }


    private fun getOrThrow(prop: Properties, name: String): String {
        return prop.getProperty(name) ?: throw IllegalArgumentException("Can't find property for $name")
    }


    fun main() {
        LOG.info("Starting")
        println("Hello World")

        val prop = Properties()
        val file = File("qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val username = getOrThrow(prop, "username")
        val password = getOrThrow(prop, "password")
        val host = getOrThrow(prop, "host")
        val controller = Controller(username, password, host)
        controller.run()

    }
}

fun main() {
    Main().main()
}
