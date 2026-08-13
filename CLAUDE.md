# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository currently is

A **stock Kotlin Multiplatform wizard template** (Android + iOS, Compose Multiplatform UI) that is
intended to be grown into the architecture described by the bundled `refactored-architecture` skill.
Nothing in `shared/` implements that architecture yet — it is `Greeting`/`Platform`/`App` scaffolding.

Do not treat the existing flat `shared/src/commonMain/kotlin/com/mkilci/kmparchitect/` layout as the
target structure. Treat it as the starting point of a migration.

Base package: `com.mkilci.kmparchitect`. Android applicationId: `com.mkilci.kmparchitect`.
iOS framework `baseName = "Shared"`, static. Gradle project name: `KmpArchitectSampleApp`.

## The architecture skill is the spec

`.claude/skills/refactored-architecture/` is the authoritative design contract for this project —
read it before proposing or writing structural code. Invoke it with the `refactored-architecture`
skill for greenfield module work, migrations, new features, DI changes, or architecture review.

**Its goal is module-level build isolation**, not layering for its own sake: a developer working on
one feature builds and runs only that feature. Layering is the means. When a rule and convenience
conflict, the rule that protects the small build wins.

```text
presentation/* ──► domain/* ◄── data/*
       │               │            │
       └──────────► core/* ◄─────────┘
app/shared ──► presentation/domain/core + selected shared data impls
platform hosts ──► app/shared
fixtures/<feature> ──► only its own domain/<feature>
sample/<feature> ──► presentation + domain + fixtures of that feature only
```

Hard rules that constrain nearly every change:

- **`sample → data` is forbidden.** This is the load-bearing rule — it keeps network, persistence,
  serialization, and vendor SDKs out of the feature development loop.
- **No feature depends on another feature.** Cross-feature decisions belong to `app/shared` only.
- `implementation` by default; every `api` edge needs a recorded justification.
- No aggregating compile-time DI codegen in the feature build path — that is why the container is
  runtime (Koin), and why every composition root owes a graph-startup test.
- `domain/*` is pure Kotlin — no Compose, no Koin, no Android/UIKit, no DTOs.
- MVI keeps **Action, Event, Effect** separate. Reducers are pure/synchronous/immutable.
  Effects are typed, one-shot, channel-delivered — never stored in replaying state. Send them only
  from `viewModelScope` so the fail-fast transport cannot trip during normal teardown.
- Deterministic fakes live in `fixtures/<feature>`, consumed by both that feature's tests and its
  sample — never duplicated between them.
- Android executables (`com.android.application`) stay separate from KMP library modules.
- Prefer interfaces + DI over `expect/actual`; reserve `expect/actual` for small primitives.

Two verification commands, kept separate: `architectureCheck` (declared edges + source rules) and
`isolationCheck` (each sample's *resolved* graph against a checked-in allowlist file). Folder shape
is not evidence of isolation; a resolved graph is.

Reference docs (read the relevant one before touching that area):
`references/module-blueprints.md` (module creation/moves), `references/mvi-navigation-di.md`
(screens, effects, navigation, Koin), `references/build-system.md` (Gradle/targets/convention
plugins), `references/platform-hosts-samples.md` (hosts, samples, framework embedding),
`references/architecture-verification.md` (checks, CI gates, benchmarks).

## Commands

```bash
./gradlew :androidApp:assembleDebug            # Android app
./gradlew :shared:testAndroidHostTest          # JVM/host unit tests for shared
./gradlew :shared:iosSimulatorArm64Test        # iOS unit tests (macOS only)
./gradlew check                                # all verification tasks
./gradlew :shared:compileKotlinIosSimulatorArm64   # iOS compile check (macOS only)
```

Run a single test: `./gradlew :shared:testAndroidHostTest --tests "com.mkilci.kmparchitect.SharedCommonTest.example"`

iOS app: open `iosApp/` in Xcode. Bundle ID and versions come from
`iosApp/Configuration/Config.xcconfig` (`TEAM_ID` is intentionally blank).

There is no lint/format task configured; `kotlin.code.style=official` is the only style setting.

Once architecture checks exist, the skill expects them behind `./gradlew architectureCheck` and
`./gradlew isolationCheck` — add them there rather than as ad-hoc scripts.

### Host limitations

This checkout runs on **Linux**. Kotlin/Native iOS targets and Xcode builds cannot be compiled or run
here — only Android and common/host-JVM work is verifiable locally. Never report iOS build or runtime
success that was not actually executed; the skill explicitly forbids claiming completion when a
required native executable was not built.

Not a git repository (`git init` has not been run).

## Build system facts

- Version catalog `gradle/libs.versions.toml` is the single source for versions — add dependencies
  there, not inline.
- AGP 9 with the **`com.android.kotlin.multiplatform.library`** plugin: `shared/` configures Android
  inside the `kotlin { android { ... } }` block (`withHostTest`, `withDeviceTestBuilder`), not a
  top-level `android { }` block. `androidApp/` uses the classic `com.android.application` DSL.
- Gradle 9.1, configuration cache and build cache **on** — keep build logic configuration-cache safe.
- Daemon JVM is pinned to Azul JDK 21 via `gradle/gradle-daemon-jvm.properties`; Kotlin/Java target
  is JVM 11. compileSdk/targetSdk 36, minSdk 24.
- Repositories are declared centrally in `settings.gradle.kts` with `google()` content filtering.
- Compose resources are generated into `kmparchitectsampleapp.shared.generated.resources.Res`.
- Repeated Gradle configuration must move into convention plugins (`build-logic` included build)
  before it is copied into a third module.
