plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("io.quarkus")
}


dependencies {
    val qv = rootProject.ext.get("quarkusVersion") as String

    implementation("io.quarkus:quarkus-kotlin")
    implementation(enforcedPlatform("io.quarkus:quarkus-universe-bom:${qv}"))
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation("io.vertx:vertx-mqtt")
    implementation(project(":qbuslib"))
    implementation("com.influxdb:influxdb-client-java:1.13.0")

    //testImplementation("io.quarkus:quarkus-junit5")
    //testImplementation("io.kotest:kotest-runner-junit5-jvm:4.3.0")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("io.vertx:vertx-junit5:3.9.+")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.13.0")
}

group = "org.acme"
version = "1.0.0-SNAPSHOT"

quarkus {
    setOutputDirectory("$projectDir/build/classes/kotlin/main")
}

/*quarkusDev {
    setSourceDir("$projectDir/src/main/kotlin")
}*/

allOpen {
    annotation("javax.ws.rs.Path")
    annotation("javax.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
        javaParameters = true
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

