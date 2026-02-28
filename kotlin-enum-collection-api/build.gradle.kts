plugins {
    id("enumcollection.kmp")
    id("module-maven-publish")
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
