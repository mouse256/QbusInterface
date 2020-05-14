plugins {
    id("org.jetbrains.kotlin.jvm").version("1.3.41")
    id("org.jetbrains.kotlin.plugin.allopen").version("1.3.41")
    id("io.quarkus")
}


dependencies {
    val qv = rootProject.ext.get("quarkusVersion") as String

    implementation("io.quarkus:quarkus-kotlin")
    implementation(enforcedPlatform("io.quarkus:quarkus-universe-bom:${qv}"))
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-resteasy-jackson")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation(project(":qbuslib"))

    testImplementation("io.quarkus:quarkus-junit5")
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

/*compileKotlin {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11
    kotlinOptions.javaParameters = true
}

compileTestKotlin {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11
}*/

