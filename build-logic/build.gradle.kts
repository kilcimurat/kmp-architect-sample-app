plugins {
    `kotlin-dsl`
}

group = "com.mkilci.kmparchitect.buildlogic"

dependencies {
    compileOnly(libs.gradlePlugin.android)
    compileOnly(libs.gradlePlugin.kotlin)
    compileOnly(libs.gradlePlugin.compose)
    compileOnly(libs.gradlePlugin.composeCompiler)

    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "kmpa.kmp.library"
            implementationClass = "com.mkilci.kmparchitect.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "kmpa.kmp.compose"
            implementationClass = "com.mkilci.kmparchitect.buildlogic.KmpComposeConventionPlugin"
        }
        register("kmpFramework") {
            id = "kmpa.kmp.framework"
            implementationClass = "com.mkilci.kmparchitect.buildlogic.KmpFrameworkConventionPlugin"
        }
        register("architecture") {
            id = "kmpa.architecture"
            implementationClass = "com.mkilci.kmparchitect.buildlogic.ArchitectureConventionPlugin"
        }
        register("androidApp") {
            id = "kmpa.android.app"
            implementationClass = "com.mkilci.kmparchitect.buildlogic.AndroidAppConventionPlugin"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
