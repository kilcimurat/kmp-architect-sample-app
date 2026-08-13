plugins {
    id("kmpa.android.app")
}

android {
    defaultConfig {
        applicationId = "com.mkilci.kmparchitect"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":app:shared"))
    implementation(project(":core:database"))
    implementation(project(":core:sharing"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
}
