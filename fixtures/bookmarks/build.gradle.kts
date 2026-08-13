plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain:bookmarks"))
        }
    }
}
