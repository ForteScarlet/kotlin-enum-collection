
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("enumcollection.kmp")
    alias(libs.plugins.kotlinx.benchmark)
}

dependencies {
    add("commonMainImplementation", project(":kotlin-enum-collection-api"))
    add("commonMainImplementation", libs.kotlinx.benchmark.runtime)
}

kotlin {
    js {
        nodejs()
        // browser()
        // binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        // browser()
        // binaries.executable()
    }
}

benchmark {
    targets {
        register("jvm")
        // register("js")
        register("wasmJs")
        register("macosArm64")
        register("iosSimulatorArm64")
        register("iosArm64")
        register("linuxX64")
        register("linuxArm64")
        register("watchosSimulatorArm64")
        register("watchosArm32")
        register("watchosArm64")
        register("tvosSimulatorArm64")
        register("tvosArm64")
        register("macosX64")
        register("mingwX64")
        register("iosX64")
        register("tvosX64")
        register("watchosX64")
    }

    configurations {
        named("main") {
            iterations = 8
            warmups = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ns"
            include(".*Enum.*Benchmark.*")
        }
    }
}
