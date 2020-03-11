import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.lang.IllegalArgumentException
import java.lang.invoke.MethodHandles
import java.util.*

class Main {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    private fun getOrThrow(prop: Properties, name:String): String {
        return prop.getProperty(name) ?: throw IllegalArgumentException("Can't find property for $name")
    }

    fun main() {
        LOG.info("Starting")
        println("Hello World")

        val prop = Properties()
        val file = File("C:\\Users\\tombi\\Documents\\qbus.properties")
        FileReader(file)
            .use { fr -> prop.load(fr) }
        val username = getOrThrow(prop, "username")
        val password = getOrThrow(prop, "password")
        val host = getOrThrow(prop, "host")

        ServerConnection(host, 8446)
            .use { conn ->
                conn.readWelcome()
                Thread.sleep(1000)
                conn.writeMsgVersion()
                Thread.sleep(1000)
                conn.readData()

                Thread.sleep(1000)
                conn.login(username, password)
                Thread.sleep(1000)
                conn.readData()

                Thread.sleep(1000)
                conn.writeControllerOptions()
                Thread.sleep(1000)
                conn.readData()
                Thread.sleep(1000)
                conn.readData()
                Thread.sleep(1000)
                conn.readData()
                Thread.sleep(1000)
                conn.readData()
                Thread.sleep(1000)
                conn.readData()
            }
        LOG.info("Done")
    }
}

fun main() {
    Main().main()
}
