plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "org.jf.baksmali.main"
}

dependencies {
    compile(project(":dexlib"))
    compile(project(":util"))
    compile(kotlin("stdlib"))
}
