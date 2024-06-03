plugins {
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.allopen") version "1.9.24" apply(false)
    base
}

allprojects {
    repositories {
        mavenCentral()
    }
    /*  ext {
        //set("supportLibraryVersion", "26.0.1")
        set("quarkusVersion", "1.8.3.Final")
    }*/
}

