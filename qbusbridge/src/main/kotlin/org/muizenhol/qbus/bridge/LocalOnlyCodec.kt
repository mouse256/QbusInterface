package org.muizenhol.qbus.bridge

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec

/**
 * Codec to be used only locally
 */
internal class LocalOnlyCodec<T>(clazz: Class<T>) : MessageCodec<T, T> {
    private val name: String = this.javaClass.canonicalName + "/" + clazz.canonicalName

    override fun encodeToWire(buffer: Buffer, t: T) {
        throw UnsupportedOperationException("Should only be used locally")
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer): T {
        throw UnsupportedOperationException("Should only be used locally")
    }

    override fun transform(t: T): T {
        return t
    }

    override fun name(): String {
        return name
    }

    override fun systemCodecID(): Byte {
        return -1
    }

    companion object {
        fun <T> register(vertx: Vertx, clazz: Class<T>) {
            vertx.eventBus().registerDefaultCodec(clazz, LocalOnlyCodec(clazz))
        }

        fun <T> unregister(vertx: Vertx, clazz: Class<T>) {
            vertx.eventBus().unregisterDefaultCodec(clazz)
        }
    }

}
