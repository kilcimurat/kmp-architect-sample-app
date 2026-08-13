plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: the repository and use cases expose Article.
            api(project(":core:model"))
        }
    }
}
