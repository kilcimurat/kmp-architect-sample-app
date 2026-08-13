plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:bookmarks"))
            implementation(project(":data:articlestore"))
            implementation(libs.koin.core)
        }
    }
}
