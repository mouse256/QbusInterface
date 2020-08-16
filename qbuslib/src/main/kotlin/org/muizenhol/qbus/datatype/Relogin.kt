package org.muizenhol.qbus.datatype

class Relogin : DataType {
    //NOTE: I'm not 100% sure about this message type.
    //But seems to occur when you flash new data to the controller, meaning you have to completely start over again.
    override val typeId = DataTypeId.RELOGIN

}