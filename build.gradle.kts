plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    base
}

allprojects {
    repositories {
        mavenCentral()
    }
}

