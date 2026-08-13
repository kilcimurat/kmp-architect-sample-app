plugins {
    id("kmpa.android.app")
}

android {
    defaultConfig {
        applicationId = "com.mkilci.kmparchitect.sample.bookmarks"
    }
}

dependencies {
    implementation(project(":sample:bookmarks:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
