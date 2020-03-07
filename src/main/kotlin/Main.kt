import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class Main {
    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

    fun main() {
        LOG.info("Starting")
        println("Hello World")
        ServerConnection("192.168.1.100", 8446)
            .use { conn ->
                conn.readWelcome()
                Thread.sleep(1000)
                conn.writeMsgVersion()
                Thread.sleep(1000)
                conn.readData()
            }
        LOG.info("Done")
    }
}

fun main() {
    Main().main()
}
