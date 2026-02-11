plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

kotlin {
    explicitApi()
    jvmToolchain(8)

    compilerOptions {
        javaParameters = true
    }
}

tasks.withType<Jar>().configureEach {
    val baseName = "love.forte.tools.enumcollection"
    val suffix = project.name.removePrefix("kotlin-enum-collection-")
    val moduleName = "$baseName.${suffix.replace('-', '.')}"
    manifest.attributes["Automatic-Module-Name"] = moduleName
}

dokka {
    dokkaPublications.html {
        suppressInheritedMembers = false
        suppressObviousFunctions = false
    }

    pluginsConfiguration.html {
        footerMessage.set("© 2026 Forte Scarlet. All rights reserved.")
    }
}
