pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "qbuscomm"
include("qbuslib")
include("qbusbridge")

val haDiscoveryDir = providers.gradleProperty("haDiscoveryDir")
includeBuild(if (haDiscoveryDir.isPresent) haDiscoveryDir else "../homeassistant-discovery")


