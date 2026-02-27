plugins {
    id("enumcollection.jvm")
    alias(libs.plugins.ksp)
    application
}

dependencies {
    implementation(project(":kotlin-enum-collection-api"))
    ksp(project(":kotlin-enum-collection-ksp"))
}

ksp {
    arg("love.forte.tools.enumcollection.inheritanceMode", "AUTO")
    arg("love.forte.tools.enumcollection.enumSets", "java.time.DayOfWeek,java.nio.file.AccessMode")
    arg("love.forte.tools.enumcollection.enumMaps", "java.time.Month")

    arg("love.forte.tools.enumcollection.basePackage", "love.forte.tools.enumcollection.examples.ksp.external.generated")
    arg("love.forte.tools.enumcollection.baseVisibility", "PUBLIC")

    arg("love.forte.tools.enumcollection.java.time.DayOfWeek.name", "WeekDaySet")
    arg(
        "love.forte.tools.enumcollection.java.time.DayOfWeek.package",
        "love.forte.tools.enumcollection.examples.ksp.external.overrides"
    )
    arg("love.forte.tools.enumcollection.java.time.DayOfWeek.visibility", "INTERNAL")
}

application {
    mainClass.set("love.forte.tools.enumcollection.examples.ksp.external.ExternalKspConfigExampleKt")
}
