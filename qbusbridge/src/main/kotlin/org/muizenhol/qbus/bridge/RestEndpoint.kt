package org.muizenhol.qbus.bridge

import org.muizenhol.qbus.Common
import org.muizenhol.qbus.sddata.SdDataJson
import org.muizenhol.qbus.sddata.SdDataStruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import javax.enterprise.inject.Default
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Application
import javax.ws.rs.core.MediaType


@Path("/qbus")
class ExampleResource {

    @Inject
    @field: Default
    lateinit var controller: ControllerHandler

    @GET
    @Path("hello")
    //@Produces("application/rss+xml;charset=UTF-8")
    fun hello(): String {
        return "hello: ${controller.dataHandler}\n"
    }

    @GET
    @Path("raw")
    @Produces(MediaType.APPLICATION_JSON)
    fun raw(): SdDataStruct? {
        return controller.dataHandler?.data
    }


    companion object {
        private const val HLN = "HLN"
        private const val HOST = "https://muizenhol.no-ip.org/hln/"
        private val LOG: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}