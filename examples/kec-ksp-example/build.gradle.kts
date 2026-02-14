plugins {
    id("enumcollection.jvm")
    alias(libs.plugins.ksp)
    application
}

dependencies {
    implementation(project(":kotlin-enum-collection-annotations"))
    implementation(project(":kotlin-enum-collection-api"))

    ksp(project(":kotlin-enum-collection-ksp"))
}

ksp {
    // Let generated types implement EnumSet/EnumMap when available.
    arg("love.forte.tools.enumcollection.inheritanceMode", "AUTO")
}

application {
    mainClass.set("love.forte.tools.enumcollection.examples.ksp.KspExampleKt")
}
