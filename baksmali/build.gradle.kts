plugins {
    application
}

application {
    mainClassName = "org.jf.baksmali.main"
}

dependencies {
    compile(project(":dexlib"))
    compile(project(":util"))
}
