plugins {
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

