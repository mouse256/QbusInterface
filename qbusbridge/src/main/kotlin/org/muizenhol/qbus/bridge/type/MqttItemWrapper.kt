package org.muizenhol.qbus.bridge.type

import org.muizenhol.qbus.sddata.SdOutput
import org.muizenhol.qbus.sddata.SdOutputDimmer
import org.muizenhol.qbus.sddata.SdOutputOnOff
import org.muizenhol.qbus.sddata.SdOutputThermostat
import java.lang.IllegalStateException

data class MqttItemWrapper(val serial: String, val data: SdOutput)
