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

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
