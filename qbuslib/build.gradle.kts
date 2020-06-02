plugins {
  //  java
    kotlin("jvm")
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    //testCompile("junit", "junit", "4.12")
    implementation("org.slf4j:slf4j-api:1.7.+")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.+")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")

    //runtimeOnly("org.slf4j:slf4j-simple:1.7.+")
    //testImplementation("io.kotest:kotest-runner-junit5-jvm:4.0.0-BETA1")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    // https://mvnrepository.com/artifact/org.hamcrest/hamcrest
    testImplementation("org.hamcrest:hamcrest:2.2")

}

tasks.withType<Test> {
    useJUnitPlatform()
    /*testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }*/
}
configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}
tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }
}
