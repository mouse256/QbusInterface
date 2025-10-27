# About

Interface for communicating with [Qbus](https://qbus.be)

It can be used to integrate with OpenHab or HomeAssistant

# Building
```
./gradlew quarkusBuild -Dquarkus.package.type=uber-jar
```

This will produce an executable java file in:
```
 qbusbridge/build/qbusbridge-1.0.0-SNAPSHOT-runner.jar
```

# Running

Once the QbusBridge is running, you can generate openhab config by executing:
```
curl 127.0.0.1:8096/qbus/openhab/items
curl 127.0.0.1:8096/qbus/openhab/things
```

