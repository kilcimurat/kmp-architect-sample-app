plugins {
    id("kmpa.android.app")
}

android {
    defaultConfig {
        applicationId = "com.mkilci.kmparchitect.sample.article"
    }
}

dependencies {
    implementation(project(":sample:article:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
