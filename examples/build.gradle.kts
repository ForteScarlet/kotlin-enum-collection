plugins {
    id("enumcollection.kmp")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-enum-collection-api"))
            }
        }
    }
}
