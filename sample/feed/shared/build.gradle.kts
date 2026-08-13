plugins {
    id("kmpa.kmp.framework")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":presentation:feed"))
            implementation(project(":domain:feed"))
            implementation(project(":fixtures:feed"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:navigation"))
            implementation(project(":core:ui"))

            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Deliberately absent: :data:feed, :app:*, every other feature.
            // See isolation-allowlist.txt -- isolationCheck fails the build if that changes.
        }
    }
}
