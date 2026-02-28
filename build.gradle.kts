plugins {
    id("org.jetbrains.dokka")
}

allprojects {
    group = "love.forte.tools.enumcollection"
    version = "0.0.1"
    description = "A Kotlin multiplatform library and ksp generator for creating high-performance collection implementations (e.g., Map, Set) based on Enums."

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