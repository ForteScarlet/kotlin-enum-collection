plugins {
    id("enumcollection.jvm")
    application
}

dependencies {
    implementation(project(":kotlin-enum-collection-api"))
}

application {
    mainClass.set("love.forte.tools.enumcollection.examples.api.ApiExampleKt")
}
