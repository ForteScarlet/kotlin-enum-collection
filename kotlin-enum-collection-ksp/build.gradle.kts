plugins {
    id("enumcollection.jvm")
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(project(":kotlin-enum-collection-annotations"))
    api(libs.codegentle.ksp)
}

