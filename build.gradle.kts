plugins {
    base
    kotlin("jvm") version "1.2.70"
}

subprojects {
    apply(plugin = "java")

    repositories {
        jcenter()
    }
}