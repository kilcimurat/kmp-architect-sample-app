plugins {
    id("kmpa.android.app")
}

android {
    defaultConfig {
        // Unique id so every sample can sit on one emulator next to the production app.
        applicationId = "com.mkilci.kmparchitect.sample.feed"
    }
}

dependencies {
    implementation(project(":sample:feed:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
