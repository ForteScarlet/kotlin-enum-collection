pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-enum-collection"

include(
    ":kotlin-enum-collection-annotations",
    ":kotlin-enum-collection-ksp",
    ":kotlin-enum-collection-api",
    ":kotlin-enum-collection-benchmark",
    ":examples",
    ":examples:kec-api-example",
    ":examples:kec-ksp-example",
)
