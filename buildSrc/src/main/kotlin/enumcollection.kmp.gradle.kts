import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    jvmToolchain(8)
    jvm {
        compilerOptions {
            javaParameters = true
        }
    }

    js(IR) {
        browser()
        nodejs()
        binaries.library()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        binaries.library()
    }

    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
}

tasks.withType<Jar>().configureEach {
    val baseName = "love.forte.tools.enumcollection"
    val suffix = project.name.removePrefix("kotlin-enum-collection-")
    val moduleName = "$baseName.${suffix.replace('-', '.')}"
    manifest.attributes["Automatic-Module-Name"] = moduleName
}

tasks.withType<DokkaTask>().configureEach {
    failOnWarning.set(true)
    dokkaSourceSets.configureEach {
        reportUndocumented.set(true)
        skipDeprecated.set(false)
    }
}
