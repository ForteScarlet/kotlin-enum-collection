plugins {
    id("enumcollection.jvm")
    alias(libs.plugins.ksp)
    application
}

dependencies {
    implementation(project(":kotlin-enum-collection-annotations"))
    ksp(project(":kotlin-enum-collection-ksp"))
}

ksp {
    arg("love.forte.tools.enumcollection.inheritanceMode", "NEVER")
}

application {
    mainClass.set("love.forte.tools.enumcollection.examples.ksp.KspExampleKt")
}
