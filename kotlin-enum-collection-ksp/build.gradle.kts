plugins {
    id("enumcollection.kmp")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-enum-collection-annotations"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ksp.symbol.processing.api)
            }
        }
    }
}
