plugins {
    id("kmpa.kmp.compose")
    // Typed navigation routes are @Serializable, so the serialization plugin belongs to the modules
    // that declare routes -- not to every Compose module.
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:feed"))
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:ui"))

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            // The one deterministic fake, shared with the sample instead of re-written here.
            implementation(project(":fixtures:feed"))
        }
    }
}
