package org.muizenhol.qbus.exception

import java.lang.RuntimeException

open class QbusException(msg: String): RuntimeException(msg)

class LoginException(msg: String): QbusException(msg)

class InvalidSerialException(msg: String): QbusException(msg)
