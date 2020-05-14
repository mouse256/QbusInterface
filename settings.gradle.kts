

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("io.quarkus").version("1.4.2.Final")
    }
}
rootProject.name = "qbuscomm"
include("qbuslib")
include("qbusbridge")


