plugins {
    id("kmpa.kmp.framework")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":presentation:bookmarks"))
            implementation(project(":domain:bookmarks"))
            implementation(project(":fixtures:bookmarks"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:navigation"))
            implementation(project(":core:ui"))

            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Deliberately absent: :presentation:article. The OpenArticle effect is handled by a
            // sample-local placeholder instead -- importing the article feature to satisfy one
            // navigation call would double this sample's graph.
        }
    }
}
