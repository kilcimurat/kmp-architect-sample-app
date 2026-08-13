# Toolchain findings (slice 0)

Results of the compatibility spike run before any architecture module was written. Every claim here
was produced by executing the command, not by reading documentation.

The original compatibility spike ran on Linux. The final architecture validation also ran on an
Apple Silicon Mac (12 logical cores, 32 GB RAM), with the Gradle daemon JVM 21 pinned by
`gradle/gradle-daemon-jvm.properties`. Historical Linux-only findings below are retained where they
explain why macOS gates are required; current host results are called out explicitly.

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

**4. The whole modular stack compiles together.** Compose MP + navigation-compose + Koin + Ktor
(including `MockEngine`) + SQLDelight + coroutines + serialization + datetime pass the 30-project
production and sample build graphs. `check isolationCheck`, all four Android APK builds and all four
iOS simulator framework links are green.

**5. iOS requires a macOS gate; that gate now passes.**

| Task | Outcome on Linux |
|---|---|
| `compileKotlinIosSimulatorArm64` | **executes**, produces a real klib under `build/classes/kotlin/iosSimulatorArm64/main/klib/` |
| `linkDebugFrameworkIosSimulatorArm64` | **SKIPPED** — no Apple target in the Linux Konan distribution (`android_*`, `linux_*`, `mingw_x64` only) |
| `iosSimulatorArm64Test` | disabled: "simulator tests require macOS" |

On the final macOS validation, production plus all three sample frameworks linked, XcodeGen created
both projects, and all four Swift hosts built for an arm64 iOS Simulator. See `docs/01-ios.md`.

**Trap worth naming:** the skipped link task still reports `BUILD SUCCESSFUL`. A Linux CI job that
runs `linkDebugFrameworkIosSimulatorArm64` will go green without linking anything. iOS gates must
run on macOS runners, and a green Linux build must never be reported as iOS verification.

**6. Gradle Isolated Projects is not compatible with the completed architecture plugin.**

The two-project spike passed, but the required re-check at 30 projects fails deterministically: the
root `kmpa.architecture` plugin reads each subproject's configurations through `allprojects`, which
violates project isolation. The flag remains off. The wrapper labels the Gradle 9.7 behavior
incubating; status and compatibility must be re-verified on upgrades because the official feature
guidance is version-sensitive.

This is an optional build-performance optimization, not a Clean Architecture correctness gap. The
architecture and runtime-classpath gates pass without it; `docs/02-benchmarks.md` records the exact
failure and migration direction.

## Deliberately not suppressed

`kotlin.native.ignoreDisabledTargets=true` remains unset. On non-macOS hosts, disabled-target noise
is a useful reminder that Apple binaries and tests need the separate macOS gate.
