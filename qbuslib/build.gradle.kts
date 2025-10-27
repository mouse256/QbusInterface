import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation (libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.databind)

    testImplementation(libs.kotest.runner.junit5.jvm)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.hamcrest)
    testImplementation(libs.log4j.slf4j2.impl)

}

tasks.withType<Test> {
    useJUnitPlatform()
    /*testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }*/
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    target {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
}
