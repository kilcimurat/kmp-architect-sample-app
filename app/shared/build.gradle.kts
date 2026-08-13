plugins {
    id("kmpa.kmp.framework")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Every feature's presentation and domain, plus the data implementations this root
            // selects. This is the one module allowed to know about all three features.
            implementation(project(":presentation:feed"))
            implementation(project(":presentation:article"))
            implementation(project(":presentation:bookmarks"))
            implementation(project(":domain:feed"))
            implementation(project(":domain:article"))
            implementation(project(":domain:bookmarks"))
            implementation(project(":data:feed"))
            implementation(project(":data:article"))
            implementation(project(":data:bookmarks"))
            implementation(project(":data:articlestore"))

            implementation(project(":core:common"))
            implementation(project(":core:database"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:model"))
            implementation(project(":core:navigation"))
            implementation(project(":core:network"))
            implementation(project(":core:sharing"))
            implementation(project(":core:ui"))

            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
        }
        commonTest.dependencies {
            implementation(libs.koin.test)
        }
    }
}
