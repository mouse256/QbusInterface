

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("io.quarkus").version("1.8.3.Final")
    }
}
rootProject.name = "qbuscomm"
include("qbuslib")
include("qbusbridge")


