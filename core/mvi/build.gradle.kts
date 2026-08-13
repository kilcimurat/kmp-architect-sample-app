plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: MviViewModel extends androidx ViewModel, so every module that
            // subclasses it needs ViewModel on its own compile classpath. Recorded in the
            // architecture check's api allowlist.
            api(libs.androidx.lifecycle.viewmodel)
        }
    }
}
