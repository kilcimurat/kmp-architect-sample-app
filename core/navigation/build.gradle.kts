plugins {
    id("kmpa.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: HandleEffects takes a Flow<F : ScreenEffect>, so callers need the
            // ScreenEffect type. Recorded in the architecture check's api allowlist.
            api(project(":core:mvi"))

            // HandleEffects collects only while the host is STARTED, so it needs the lifecycle
            // owner from the composition. `implementation`: no public signature here names it.
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
    }
}
