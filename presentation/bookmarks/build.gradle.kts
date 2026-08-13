plugins {
    id("kmpa.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:bookmarks"))
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
            implementation(project(":fixtures:bookmarks"))
        }
    }
}
