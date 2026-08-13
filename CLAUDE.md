# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A **reference KMP application** (Android + iOS, Compose Multiplatform) built to demonstrate and
measure the architecture in the bundled `refactored-architecture` skill. 30 Gradle projects, three
features (`feed`, `article`, `bookmarks`), each with its own runnable sample app on both platforms.

An offline-first article reader: a Ktor client syncs into a SQLDelight store, and local storage is
the single observable truth. The demo backend is a `MockEngine` selected in `app/shared`, so the
client, content negotiation, DTO parsing and sync are all real while results stay reproducible.

```text
build-logic/                 convention plugins + architecture rules (own unit tests)
app/{shared,android}         root graph, cross-feature routing, thin Android host
core/{mvi,navigation,common,model,designsystem,ui,network,database,sharing}
domain/{feed,article,bookmarks}        pure Kotlin
data/{feed,article,bookmarks}          + data/articlestore (declared shared infrastructure)
fixtures/{feed,article,bookmarks}      deterministic fakes, shared by tests and samples
presentation/{feed,article,bookmarks}
sample/{feed,article,bookmarks}/{shared,androidApp}   + isolation-allowlist.txt each
iosApp/ iosSamples/          XcodeGen specs, generated projects are git-ignored
```

Base package `com.mkilci.kmparchitect`. Frameworks: `AppShared`, `FeedSample`, `ArticleSample`,
`BookmarksSample` (derived from the Gradle path — check with `:<module>:printFrameworkName`).

`docs/` records what was measured rather than assumed: `00-toolchain.md` (version constraints found
by building), `01-ios.md` (what is and is not verified on Linux, plus a first-Mac-run checklist),
`02-benchmarks.md` (build-isolation measurements).

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
  Effects are typed, one-shot, channel-delivered — never stored in replaying state. Emission is
  bounded by scope ownership, not by coroutines: synchronous effects may come straight from a
  ViewModel-owned method (`onAction` calling `sendEffect` is correct); asynchronous ones must come
  from `viewModelScope`, which is cancelled before the transport closes; never from an unowned scope.
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
# Gates — run these before claiming anything structural works
./gradlew architectureCheck isolationCheck buildLogicTest
./gradlew check                                    # all module tests

# The daily loop: build and run ONE feature
./gradlew :sample:feed:androidApp:installDebug     # also :article, :bookmarks

# Production
./gradlew :app:android:assembleDebug
./gradlew :app:shared:compileKotlinIosSimulatorArm64   # iOS Kotlin compiles here; linking does not

# Evidence
./gradlew :sample:feed:androidApp:dependencies --configuration debugRuntimeClasspath
./gradlew :app:shared:printFrameworkName
./scripts/benchmark.sh
./scripts/generate-xcode-projects.sh               # macOS, needs `brew install xcodegen`
```

Single test: `./gradlew :presentation:feed:testAndroidHostTest --tests "*FeedViewModelTest*"`

Adding a rule or convention plugin? The rules are plain Kotlin in `build-logic/src/main/kotlin/.../rules/`
with unit tests beside them — add the rejection fixture in the same commit, or the rule is unproven.

No lint/format task is configured; `kotlin.code.style=official` is the only style setting.

### Host limitations

This checkout runs on **Linux**, and the split matters:

- iOS **Kotlin compiles here** — `compileKotlinIos{SimulatorArm64,Arm64}` genuinely execute and
  produce klibs, so iosMain source errors are caught locally.
- iOS **linking does not**. `linkDebugFrameworkIosSimulatorArm64` is **silently SKIPPED and still
  reports `BUILD SUCCESSFUL`**. Never read that as iOS verification, and never put an iOS link or
  Xcode step in a Linux CI job.
- Android is fully verifiable: AVD `sugaria_pixel6a_api36` boots here.

## Build system facts

- Version catalog `gradle/libs.versions.toml` is the single source for versions — add dependencies
  there, not inline.
- AGP 9 with the **`com.android.kotlin.multiplatform.library`** plugin: KMP libraries configure
  Android inside the `kotlin { android { ... } }` block. In plugin code that block is a
  `KotlinMultiplatformAndroidLibraryTarget`, not the bare extension interface — only the target
  exposes `compilerOptions`. Executables use the classic `com.android.application` DSL.
- Gradle 9.7, configuration cache and build cache **on** — keep build logic configuration-cache safe.
- lifecycle is pinned to `2.11.0-beta01`: stable 2.11.0 requires AGP 9.1.0 and compileSdk 37. Raise
  all three together or not at all.
- `kotlinx.datetime.Clock` was removed in datetime 0.8.0 — use `kotlin.time.Clock`.
- SQLite upsert (`ON CONFLICT DO UPDATE`) needs SQLite 3.24 = API 30. minSdk here is 24, so
  `Article.sq` uses insert-then-update; the modern syntax compiles and fails on device.
- Daemon JVM is pinned to Azul JDK 21 via `gradle/gradle-daemon-jvm.properties`; Kotlin/Java target
  is JVM 11. compileSdk/targetSdk 36, minSdk 24.
- Repositories are declared centrally in `settings.gradle.kts` with `google()` content filtering.
- Two config files drive the architecture rules: `config/api-allowlist.txt` (every justified `api`
  edge, with its reason) and `config/infrastructure-modules.txt` (data modules that are shared
  plumbing rather than a feature). Both are meant to be edited in review, not silently.
- Repeated Gradle configuration must move into convention plugins (`build-logic` included build)
  before it is copied into a third module.
