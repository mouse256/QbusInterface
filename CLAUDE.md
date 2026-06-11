# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

QbusInterface bridges a [Qbus](https://qbus.be) home automation controller to MQTT, enabling integration with HomeAssistant and OpenHab. The Qbus controller communicates over a proprietary binary TCP protocol on port 8446.

## Build & Run Commands

```bash
# Build uber-jar
./gradlew quarkusBuild -Dquarkus.package.jar.type=uber-jar

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :qbuslib:test
./gradlew :qbusbridge:test

# Run a single test class
./gradlew :qbuslib:test --tests "ControllerTest"

# Run the application
QBUS_PROPERTY_FILE=qbus.properties java -jar qbusbridge/build/qbusbridge-1.0.0-SNAPSHOT-runner.jar
```

The app listens on port 8096 (configured in `qbusbridge/src/main/resources/application.properties`).

## Configuration

The app reads config from a `.properties` file pointed to by `QBUS_PROPERTY_FILE` env var (defaults to `/tmp/qbus.properties`). See `qbus-sample.properties` for the required keys:

```properties
environment=prod    # prod or dev
username=xxx
password=yyy
host=192.168.1.100  # Qbus controller IP
serial=123456       # Qbus controller serial
mqtt.host=127.0.0.1
mqtt.port=1883      # optional, defaults to 1883
```

## Project Structure

Two Gradle submodules:

- **`qbuslib`** — Pure Kotlin library implementing the Qbus TCP protocol. No framework dependencies; tested independently.
- **`qbusbridge`** — Quarkus application that bridges qbuslib to MQTT. Depends on `qbuslib` and the sibling composite build `../homeassistant-discovery`.

The `settings.gradle.kts` uses `includeBuild` to pull in `../homeassistant-discovery` as a composite build. This sibling project must exist locally.

## Architecture

### qbuslib — Qbus Protocol Layer

`Controller` is a state machine (states: `INIT → WAIT_FOR_VERSION → LOGIN → WAIT_FOR_CONTROLLER_OPTIONS → WAIT_FOR_FAT_DATA → DOWNLOAD_SD_DATA_HEADER → DOWNLOAD_SD_DATA → FETCH_STATE → READY`) that manages the login handshake and data download sequence.

`ServerConnection` / `ServerConnectionImpl` — handles raw TCP I/O. Messages use a fixed 9-byte prefix (`QBUS\0\0\0\0\0`), followed by a length-prefixed frame ending with stop byte `0x23`.

`SdDataParser` — downloads the controller's SD card data (a ZIP containing `JSONData.tmp`), parses it into `SdDataStruct`. This is the device configuration — all outputs (lights, dimmers, thermostats, etc.) with their addresses.

`SdOutput` sealed class hierarchy — represents each Qbus output type:
- `SdOutputOnOff` — relay/switch
- `SdOutputDimmer` — dimmable light
- `SdOutputTimer` / `SdOutputTimer2` — timer/staircase outputs
- `SdOutputThermostat` — climate control (subaddress layout differs between events and addressStatus reads)
- `SdOutputAudio` / `SdOutputAudioGroup` — audio zones

`DataHandler` — holds the parsed `SdDataStruct` and routes incoming `Event`/`AddressStatus` messages to the correct `SdOutput`, firing a listener `(serial, SdOutput)` on state change.

### qbusbridge — MQTT Bridge Layer

Quarkus CDI bean `ControllerHandler` (`@ApplicationScoped @Startup`) creates a Vert.x instance and deploys two verticles:

- **`QbusVerticle`** — wraps `Controller`. On `onControllerReady`, registers a `DataHandler` listener that publishes updates to the Vert.x event bus (`MqttVerticle.ADDRESS`). Listens on the event bus for incoming MQTT commands (`ADDRESS_UPDATE_QBUS_ITEM`) and forwards them to `controller.requestNewQbusState`.

- **`MqttVerticle`** — manages the MQTT connection (Vert.x MQTT client with auto-reconnect). Translates between MQTT topics and Qbus outputs.

The two verticles communicate exclusively via the Vert.x local event bus using `LocalOnlyCodec` (bypasses serialization for in-process messages). All codec types must be registered in `ControllerHandler.registerVertxCodecs`.

### MQTT Topic Convention

```
qbus/{serial}/sensor/{type}/{id}/state      # published state
qbus/{serial}/sensor/{type}/{id}/command    # subscribed for commands
qbus/{serial}/info/outputs/{type}           # list of outputs by type
qbus/{serial}/state                         # full state snapshot (JSON)
```

Types: `on_off`, `dimmer`, `thermostat`, `event` (see `MqttType` enum).

HomeAssistant auto-discovery messages are published to `homeassistant/device/qbus-mqtt-{serial}/...`.

### REST Endpoints (port 8096)

- `GET /qbus/raw` — raw `SdDataStruct` JSON
- `GET /qbus/update` — trigger re-publish of all states
- `GET /qbus/openhab/things` — OpenHab things config
- `GET /qbus/openhab/items` — OpenHab items config

## Testing Approach

`qbuslib` tests use `StubbedServerConnection` to simulate the Qbus TCP server without a real controller. Tests use Kotest as the test framework with JUnit5 launcher, and Mockito-Kotlin for mocking.

`qbusbridge` tests use Vert.x JUnit5 extension (`StubbedMqttServer`) to test MQTT verticle behavior.
