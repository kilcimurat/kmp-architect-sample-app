plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
    id("kmpa.architecture")
}

// Convention plugins and architecture rules are plain Kotlin, unit tested without executing Gradle.
// Wired into the root so a rule change cannot land without its tests running.
tasks.register("buildLogicTest") {
    group = "verification"
    description = "Runs unit tests for convention plugins and architecture rules."
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}