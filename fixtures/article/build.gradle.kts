plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: the fakes implement this feature's ports and expose Article.
            api(project(":domain:article"))
        }
    }
}
