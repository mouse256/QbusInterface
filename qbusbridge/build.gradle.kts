plugins {
    kotlin("jvm") // version "1.4.32"
    kotlin("plugin.allopen") // version "1.4.32"
    id("io.quarkus")
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation("io.vertx:vertx-mqtt")
    implementation(project(":qbuslib"))
    implementation("com.influxdb:influxdb-client-java:1.13.0")

    implementation(platform("software.amazon.awssdk:bom:2.16.+"))
    implementation("software.amazon.awssdk:timestreamwrite")
    implementation("software.amazon.awssdk:apache-client")

    //testImplementation("io.quarkus:quarkus-junit5")
    //testImplementation("io.kotest:kotest-runner-junit5-jvm:4.3.0")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("io.vertx:vertx-junit5:3.9.+")
    //testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.15.0") //??

    //Java SDK uses apache-commons-logging. Route over slf4j
    runtimeOnly("org.slf4j:jcl-over-slf4j")
    runtimeOnly("software.amazon.awssdk:sso")

}

group = "org.acme"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

allOpen {
    annotation("javax.ws.rs.Path")
    annotation("javax.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
    kotlinOptions.javaParameters = true
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

