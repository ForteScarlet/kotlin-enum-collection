plugins {
    id("org.jetbrains.dokka")
}

allprojects {
    group = "love.forte.tools.enumcollection"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("org.jetbrains.dokka")) {
            rootProject.dependencies.dokka(this)
        }
    }
}

dokka {
    moduleName = "Kotlin Enum Collection"
}