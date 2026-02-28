plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)

    // see https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html#configure-the-project
    // see https://github.com/vanniktech/gradle-maven-publish-plugin
    // see https://plugins.gradle.org/plugin/com.vanniktech.maven.publish
    implementation(libs.maven.publish)
}