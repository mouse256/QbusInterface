plugins {
    kotlin("jvm") version "1.4.0" apply false
    id("org.jetbrains.kotlin.plugin.allopen").version("1.4.0").apply(false)
    base
}

allprojects {
    repositories {
        mavenCentral()
    }
    ext {
        //set("supportLibraryVersion", "26.0.1")
        set("quarkusVersion", "1.4.2.Final")
    }
}

