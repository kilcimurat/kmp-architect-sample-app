plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:article"))
            implementation(project(":data:articlestore"))
            implementation(libs.koin.core)
        }
    }
}
