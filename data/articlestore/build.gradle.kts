plugins {
    id("kmpa.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:database"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
    }
}

sqldelight {
    databases {
        create("ArticleDatabase") {
            packageName.set("com.mkilci.kmparchitect.data.articlestore.db")
        }
    }
}
