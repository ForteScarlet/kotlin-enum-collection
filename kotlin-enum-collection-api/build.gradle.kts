plugins {
    id("enumcollection.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlin-enum-collection-annotations"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
