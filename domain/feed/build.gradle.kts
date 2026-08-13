plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: FeedRepository and the use cases expose Article in their signatures.
            api(project(":core:model"))
        }
    }
}
