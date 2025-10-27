import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.quarkus)
}


dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.vertx:vertx-mqtt")
    implementation(project(":qbuslib"))
    implementation("org.muizenhol:homeassistant-discovery:1.0.0")

    testImplementation(libs.hamcrest)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.vertx.junit5)
    testImplementation(libs.log4j.slf4j2.impl)

    //Java SDK uses apache-commons-logging. Route over slf4j
    runtimeOnly(libs.jcl.over.slf4j)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

group = "org.acme"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    target {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
}
tasks {
    test {
        systemProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory")
    }
}
configurations {
    testRuntimeClasspath {
        exclude(group = "org.jboss.slf4j")
    }
}

