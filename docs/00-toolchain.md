# Toolchain findings (slice 0)

Results of the compatibility spike run before any architecture module was written. Every claim here
was produced by executing the command, not by reading documentation.

Host: Linux x86_64, 128 cores, 251 GB RAM. Launcher JVM 11, Gradle daemon JVM 21 (Azul, pinned by
`gradle/gradle-daemon-jvm.properties`).

## Pinned matrix

| Component | Version | Note |
|---|---|---|
| Gradle | 9.7.0 | upgraded from 9.1 for Isolated Projects |
| Kotlin | 2.4.10 | from the project wizard |
| AGP | 9.0.1 | from the project wizard; stable 9.3.1 exists |
| Compose Multiplatform | 1.11.1 | material3 1.11.0-alpha07 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |
| androidx lifecycle (JetBrains) | 2.11.0-**beta01** | see constraint below |
| navigation-compose (JetBrains) | 2.9.2 | published against Kotlin 2.2.20, consumed from 2.4.10 |
| coroutines | 1.11.0 | |
| kotlinx-serialization | 1.11.0 | |
| kotlinx-datetime | 0.8.0 | see API change below |
| Koin | 4.2.2 | |
| Ktor | 3.5.2 | |
| SQLDelight | 2.3.2 | |

## Findings

**1. lifecycle 2.11.0 stable is not reachable on this matrix.**

```
Dependency 'androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0' requires
  libraries and applications that compileSdk of at least 37.
Dependency 'androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0' requires
  Android Gradle plugin 9.1.0 or higher. This build currently uses 9.0.1.
```

Pinned back to `2.11.0-beta01`, the version the project was generated with. Moving to stable
lifecycle means raising AGP to ≥ 9.1.0 and compileSdk to 37 together — a deliberate, separate
upgrade, not a side effect.

**2. `kotlinx.datetime.Clock` is gone in datetime 0.8.0.**

`Clock` moved to the standard library. Use `kotlin.time.Clock` with `@OptIn(ExperimentalTime::class)`
(or the `0.8.0-0.6.x-compat` artifact to keep the old API). This project uses the stdlib clock, and
injects it as a port so tests and fixtures can seed it.

**3. SQLDelight 2.3.2 works with the AGP 9 KMP library plugin.**

The `app.cash.sqldelight` plugin applied cleanly alongside `com.android.kotlin.multiplatform.library`
and generated the database class from a `.sq` file. This was the biggest unknown going in.

**4. The whole stack compiles together.** Compose MP + navigation-compose + Koin + Ktor (incl.
`MockEngine`) + SQLDelight + coroutines + serialization + datetime, in one module,
`:androidApp:assembleDebug` → `BUILD SUCCESSFUL`.

**5. iOS: klib compilation works on Linux, framework linking does not.**

| Task | Outcome on Linux |
|---|---|
| `compileKotlinIosSimulatorArm64` | **executes**, produces a real klib under `build/classes/kotlin/iosSimulatorArm64/main/klib/` |
| `linkDebugFrameworkIosSimulatorArm64` | **SKIPPED** — no Apple target in the Linux Konan distribution (`android_*`, `linux_*`, `mingw_x64` only) |
| `iosSimulatorArm64Test` | disabled: "simulator tests require macOS" |

So all iOS-targeted Kotlin source is genuinely compile-verified on this machine; only framework
binaries, Xcode builds and simulator runs need a Mac.

**Trap worth naming:** the skipped link task still reports `BUILD SUCCESSFUL`. A Linux CI job that
runs `linkDebugFrameworkIosSimulatorArm64` will go green without linking anything. iOS gates must
run on macOS runners, and a green Linux build must never be reported as iOS verification.

**6. Gradle Isolated Projects is compatible so far.**

Status wording moves between releases and the user guide lags the release notes, so treat this as
scoped to Gradle 9.7.0 and re-verify on any upgrade. The 9.7.0 release notes say the feature
"transitions from being experimental to incubating", is not enabled by default, and is "not yet
recommended for production use". It stays an optional measured optimisation here, never a
requirement.

`-Dorg.gradle.unsafe.isolated-projects=true` ran `:androidApp:assembleDebug` successfully with
AGP 9.0.1, KMP 2.4.10, Compose and SQLDelight applied — no isolation violations reported. This is a
2-project build; it must be re-checked once the tree reaches ~29 projects, which is where the
configuration cost the feature targets actually appears.

Not enabled in `gradle.properties` yet. It stays an opt-in flag used for measurement until the full
tree is in place.

## Deliberately not suppressed

`kotlin.native.ignoreDisabledTargets=true` would silence the per-module "native task is disabled"
warnings. It is left off: the noise is the reminder that iOS tests are not running here.
