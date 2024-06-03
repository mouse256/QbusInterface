plugins {
  //  java
    kotlin("jvm")
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    //implementation(kotlin("stdlib-jdk8"))
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testRuntimeOnly ("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")

}

tasks.withType<Test> {
    useJUnitPlatform()
    /*testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }*/
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
    }
}
