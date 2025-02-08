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
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    //implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("io.vertx:vertx-mqtt")
    implementation(project(":qbuslib"))

    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("io.vertx:vertx-junit5:4.4.1")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.21.1")

    //Java SDK uses apache-commons-logging. Route over slf4j
    runtimeOnly("org.slf4j:jcl-over-slf4j:2.0.9")

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

