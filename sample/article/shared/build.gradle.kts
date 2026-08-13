plugins {
    id("kmpa.kmp.framework")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":presentation:article"))
            implementation(project(":domain:article"))
            implementation(project(":fixtures:article"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:navigation"))
            implementation(project(":core:ui"))

            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Deliberately absent: :data:article, :data:articlestore, :app:*, other features.
        }
    }
}
