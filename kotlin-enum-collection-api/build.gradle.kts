plugins {
    id("enumcollection.kmp")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-enum-collection-annotations"))
            }
        }
    }
}
