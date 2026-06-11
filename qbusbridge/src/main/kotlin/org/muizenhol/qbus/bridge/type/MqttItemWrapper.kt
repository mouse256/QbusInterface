package org.muizenhol.qbus.bridge.type

import org.muizenhol.qbus.sddata.SdOutput

data class MqttItemWrapper(val serial: String, val data: SdOutput)