# About

Interface for communicating with [Qbus](https://qbus.be)

It can be used to integrate with OpenHab or HomeAssistant.

This software communicates with the Qbus controller, and exposes this information over MQTT.


# Building
```
./gradlew quarkusBuild -Dquarkus.package.type=uber-jar
```

This will produce an executable java file in:
```
 qbusbridge/build/qbusbridge-1.0.0-SNAPSHOT-runner.jar
```

# Running

```
QBUS_PROPERTY_FILE=qbus-sample.properties java -jar qbusbridge/build/qbusbridge-1.0.0-SNAPSHOT-runner.jar
```
## HomeAssistant

If MQTT is configured in HomeAssitant, there will be auto-discovery information
that will make everything show up automatically.

## OpenHab

Once the QbusBridge is running, you can generate openhab config by executing:
```
curl 127.0.0.1:8096/qbus/openhab/items
curl 127.0.0.1:8096/qbus/openhab/things
```

