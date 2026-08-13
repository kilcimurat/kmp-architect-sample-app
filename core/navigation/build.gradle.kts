plugins {
    id("kmpa.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: HandleEffects takes a Flow<F : ScreenEffect>, so callers need the
            // ScreenEffect type. Recorded in the architecture check's api allowlist.
            api(project(":core:mvi"))
        }
    }
}
