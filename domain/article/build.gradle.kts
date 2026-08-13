plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: the repository and use cases expose Article, and ShareArticle
            // returns the ShareResult declared by the sharing contract.
            api(project(":core:model"))
            api(project(":core:sharing"))
        }
    }
}
