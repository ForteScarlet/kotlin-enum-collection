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

    js {
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

    // https://kotlinlang.org/docs/native-target-support.html
    // Tier 1
    macosArm64()
    iosSimulatorArm64()
    iosArm64()

    // Tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosArm64()

    // Tier 3
    macosX64()
    mingwX64()
    iosX64()
    tvosX64()
    watchosX64()
}

tasks.withType<Jar>().configureEach {
    val baseName = "love.forte.tools.enumcollection"
    val suffix = project.name.removePrefix("kotlin-enum-collection-")
    val moduleName = "$baseName.${suffix.replace('-', '.')}"
    manifest.attributes["Automatic-Module-Name"] = moduleName
}

dokka {
    dokkaPublications.html {
        suppressInheritedMembers = false
        suppressObviousFunctions = false
    }

    pluginsConfiguration.html {
        footerMessage.set("© 2026 Forte Scarlet. All rights reserved.")
    }
}
