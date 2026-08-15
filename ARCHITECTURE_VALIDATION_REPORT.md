# Architecture Validation Report

Validation date: 2026-08-13 (Europe/Istanbul)  
Host: Apple Silicon macOS, 12 logical cores, 32 GB RAM, Xcode 26.2  
Architecture source: `.claude/skills/refactored-architecture/SKILL.md` and all five references

## Executive Summary

The repository now implements and proves the current SKILL's required Clean Architecture, MVI,
composition-root and feature-isolation boundaries. `architectureCheck`, `isolationCheck`, the full
multiplatform `check`, production/sample Android builds, Kotlin iOS framework links and four real
Xcode host builds pass.

The architecture gate inspected 211 declared project edges, 834 declared external edges and 85
production source files with zero violations. Resolved sample runtime graphs contain 9 projects for
Feed, 10 for Article and 9 for Bookmarks, with no production data module, forbidden external
dependency, unexpected project or stale waiver.

Final architecture status: **READY FOR V1.0**, with the explicitly recorded non-blocking risks in
the final section.

## Application Changes

- Extended architecture validation from project dependencies to declared external dependencies,
  configuration ownership and unjustified `api` exposure.
- Added source rules for data-to-UI/navigation leaks, native controller retention, composable
  repository lookup, fixture clock/random access and unowned effect scopes.
- Added a production composition-root test that starts Koin with in-memory platform adapters,
  resolves all initial ViewModels and observes deterministic state.
- Strengthened all sample graph tests so they resolve and start the sample's initial ViewModel.
- Added a full Effect-channel test proving overflow fails loudly without pretending the channel is
  closed.
- Made Xcode hosts executable on the configured KMP targets: arm64-only simulator setting, SQLite
  system link, distinct Swift host module names and iOS 18.5 deployment alignment.
- Made the benchmark portable to macOS and safe in a non-Git export, and measured both Feed and
  Bookmarks sample graphs.

## Domain Flow/StateFlow Decisions

Domain contracts remain cold `Flow` producers. Lifecycle-owned replay/state belongs to
presentation ViewModels; fixtures expose deterministic cold flows. No domain type owns UI lifetime
or imports lifecycle/Compose APIs. Architecture source and external-dependency checks enforce that
boundary.

## Effect Transport Policy

Typed effects use a bounded `Channel` and are exposed as `Flow`. Delivery is one-shot and
single-consumer; it is not durable state. `trySend` failure is a programming error: closed and full
channels fail loudly. ViewModel teardown owns channel closure.

The MVI test suite verifies normal delivery, closed-channel failure and full-channel failure with no
collector. The full-channel assertion also proves the failure is capacity pressure, not teardown.

## Reducer Verification

Reducers remain event-owned and pure: state transitions are computed from current state plus a
typed event. Presentation tests exercise loading, success, failure, bookmark and effect paths on
Android and iOS simulator test targets. No reducer performs I/O, navigation or DI lookup.

## Composition Ownership

`app/shared` owns production cross-feature composition, top-level typed navigation and the stable
iOS entry API. Platform hosts own executable lifecycle and native adapters. Each sample owns only
its feature graph plus deterministic fixtures.

The new production graph test resolves `FeedViewModel`, `ArticleViewModel` and
`BookmarksViewModel`. Feed, Article and Bookmarks sample graph tests start their Koin graphs and
resolve their initial ViewModels; Article resolution supplies its typed article parameter.

## Architecture Enforcement

Generated report: `build/reports/architecture/architecture-check.txt`

```text
project edges inspected:  211
external edges inspected: 834
sources inspected: 85
violations: 0
```

Enforcement now covers:

- outbound and inbound project-edge rules, including fixtures consumers;
- forbidden external frameworks per layer and sample/fixture production I/O isolation;
- external dependencies declared through `api` unless justified in `config/api-allowlist.txt`;
- ViewModel/composable/native-controller, data UI/navigation and fixture determinism source rules;
- narrow API exceptions for SQLDelight runtime, lifecycle ViewModel and Ktor core.

The KMP synthetic SwiftPM lock metadata configuration is excluded from declared-edge collection
because it represents source-set association rather than a real declared module dependency.

## Isolation Enforcement

`isolationCheck` resolves each Android sample's concrete `debugRuntimeClasspath`. All reports have
zero unexpected projects, stale allowlist entries and forbidden external dependencies.

| Sample | Resolved projects | Production data | Forbidden externals |
|---|---:|---:|---:|
| Feed | 9 | 0 | 0 |
| Article | 10 | 0 | 0 |
| Bookmarks | 9 | 0 | 0 |

The Article graph has one additional project for the narrowly required sharing contract.

This records the gate as it existed at validation time: Android only. It now resolves one graph per
platform — see *Post-Validation Changes*.

## MVI Tests

Targeted graph and transport tests passed with:

```text
./gradlew :core:mvi:testAndroidHostTest \
  :app:shared:testAndroidHostTest \
  :sample:feed:shared:testAndroidHostTest \
  :sample:article:shared:testAndroidHostTest \
  :sample:bookmarks:shared:testAndroidHostTest
```

The final full run executed Android and iOS simulator test tasks through `check`.

## Feature Isolation Evidence

Two independent feature samples were benchmarked, satisfying the SKILL requirement not to infer
repeatability from one hand-picked sample.

- Cold production: 236 executed tasks; Feed and Bookmarks: 125 each (47% fewer).
- Presentation edits: production 24 tasks; both feature samples 13 (46% fewer).
- Data edits: production 24 tasks; both feature samples remain at the 9-task no-change graph.
- Design-system edit: production 24; Feed and Bookmarks 13 each.

Raw repetitions are in `build/benchmark-results.tsv`; interpretation is in
`docs/02-benchmarks.md`.

## SKILL/Reference Updates

The latest project-local SKILL and all references were read completely before implementation. They
already contain the corrected fixtures-consumer wording, fail-loud Effect teardown policy,
framework-neutral DI rule and resolved-graph isolation requirement. No SKILL file needed editing.

Repository-facing README, toolchain, iOS and benchmark documentation were updated to match actual
Mac builds and current measurements. The stale wizard README and historical Linux-only iOS claims
were removed.

## Android Production Build

`:app:android:assembleDebug` passed. The APK installed on `emulator-5554`; the process remained
alive after launch. UI hierarchy inspection observed the empty Feed state, Refresh action and Feed
/ Saved navigation. Tapping Refresh populated four deterministic Demo Backend articles.

## iOS Production Build

`:app:shared:linkDebugFrameworkIosSimulatorArm64` passed. XcodeGen generated
`KmpArchitectSampleApp.xcodeproj`, and the `KmpArchitectSampleApp` scheme passed a generic iOS
Simulator build with signing disabled. On an iPhone 17 Pro simulator running iOS 26.2, the app
remained alive after launch; a clean install showed the empty state, Refresh loaded four Demo Backend
articles, and article navigation, bookmark persistence, native Share sheet and the Saved tab were
exercised successfully.

## Android Sample Builds

Feed, Article and Bookmarks debug APKs built, installed and launched. Delayed PID/log checks showed
all processes alive with no fatal exception. UI hierarchy inspection verified deterministic fixture
content and resources.

Runtime interactions verified:

- Feed article tap emitted navigation to `Outside this sample`; system Back restored the feed.
- Bookmarks article tap navigated to the same boundary screen; Back restored saved fixtures.
- Article Bookmark changed the action to `Remove bookmark`.
- Article Share stayed in the sample app and emitted the visible `Shared.` one-shot result; no
  platform chooser opened.

## iOS Sample Builds

All three Kotlin frameworks linked. XcodeGen generated one executable target per feature, and the
`FeedSample`, `ArticleSample` and `BookmarksSample` schemes each passed a generic arm64 iOS
Simulator build. All three were installed and exercised on iPhone 17 Pro: deterministic fixtures,
Feed/Bookmarks navigation and Back, Article bookmark state, and the fake `Shared.` Effect passed.

## Benchmark Results

| Scenario | Production | Feed sample | Bookmarks sample |
|---|---:|---:|---:|
| cold | 9353 ms / 236 | 3579 ms / 125 | 3478 ms / 125 |
| warm no-change | 615 ms / 20 | 569 ms / 9 | 498 ms / 9 |
| feature presentation edit | Feed 1465 / Bookmarks 1307 ms, 24 | 2009 ms / 13 | 1262 ms / 13 |
| feature data edit | Feed 766 / Bookmarks 740 ms, 24 | 450 ms / 9 | 622 ms / 9 |
| design-system edit | 1209 ms / 24 | 870 ms / 13 | 751 ms / 13 |

Exact rows and repetitions remain in the TSV. Task count, not short-run wall time, is the primary
portable result.

## Commands Executed

Primary final gates:

```text
./gradlew buildLogicTest architectureCheck
./gradlew check isolationCheck
./gradlew :app:android:assembleDebug
./gradlew :sample:feed:androidApp:assembleDebug
./gradlew :sample:article:androidApp:assembleDebug
./gradlew :sample:bookmarks:androidApp:assembleDebug
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample:feed:shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample:article:shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample:bookmarks:shared:linkDebugFrameworkIosSimulatorArm64
./scripts/generate-xcode-projects.sh
xcodebuild ... -scheme KmpArchitectSampleApp ... build
xcodebuild ... -scheme FeedSample ... build
xcodebuild ... -scheme ArticleSample ... build
xcodebuild ... -scheme BookmarksSample ... build
./scripts/benchmark.sh
```

Final `check isolationCheck`: **BUILD SUCCESSFUL**, 923 actionable tasks, including Android and iOS
simulator tests. The earlier full uncached validation also passed; the final confirmation completed
in 12 seconds using cache/up-to-date work where applicable.

## Failures/Fixes

1. Declared-edge validation initially saw a synthetic SwiftPM metadata configuration and implicit
   Kotlin stdlib as false edges. Collection now excludes that synthetic configuration and treats
   implicit stdlib correctly; real external API exposure remains enforced.
2. Generic Xcode Simulator builds initially requested unsupported x86_64. Xcode specs now exclude
   x86_64 to match configured `iosSimulatorArm64` targets.
3. Production Xcode link initially missed SQLite symbols. Both host specs now link `libsqlite3`.
4. Sample Swift targets initially shadowed imported Kotlin modules with the same names. Swift host
   module names are now `*SampleHost` while scheme/product/framework names remain stable.
5. Native ICU objects required iOS 18.5 while hosts declared 18.2. Both deployment targets now match
   18.5 and the native builds were repeated successfully.
6. Initial ADB launches used application-id-relative Activity names, while Activities live in the
   convention-plugin namespace. Fully qualified components launched successfully; this required no
   application source change.
7. All iOS executables initially aborted after successful build/install because their generated
   Info.plists lacked `CADisableMinimumFrameDurationOnPhone = true`. The key now lives in both
   authoritative XcodeGen specs, all four generated apps contain it, and runtime passes. The
   architecture SKILL and iOS/verification references now make this a required host rule.

## Post-Validation Changes

Work done after the validation run above, on a different host. Environment: AMD Threadripper PRO
5995WX, 128 cores, 251 GB RAM, Linux 6.8.0-124-generic; Gradle 9.7.0, AGP 9.0.1, Kotlin 2.4.10,
Compose Multiplatform 1.11.1, Koin 4.2.2; configuration cache and build cache enabled. No Apple
toolchain is present, so nothing here re-verifies the iOS build or runtime evidence above.

### Isolation gate extended to iOS

The gate previously resolved only `debugRuntimeClasspath` on each sample's Android executable. A
static framework links whatever reaches it exactly as an APK packages it, so the iOS half of the
isolation claim rested on "the framework links" — which a leaked data module does not prevent.

`isolationCheck` now registers two tasks per sample against one allowlist:

| Sample | Android — `debugRuntimeClasspath` | iOS — `iosSimulatorArm64CompileKlibraries` |
|---|---:|---:|
| Feed | 9 | 8 |
| Article | 10 | 9 |
| Bookmarks | 9 | 8 |

The difference is the Android executable host; the projects underneath are identical on both
platforms, which the shared allowlist now asserts rather than assumes. Each root is excluded from
its own graph, so no second allowlist file was needed. Only the simulator target is resolved: both
iOS targets are fed by the same `iosMain` source set, so `iosArm64` resolves the same projects.

Comparison logic moved from the Gradle task into `rules/IsolationRules.kt` and is covered by eight
unit tests, including a framework-rooted graph accepted by the Android allowlist and rejection
fixtures for `sample -> data` on both platforms, a foreign feature, a forbidden external that adds no
project node, and a stale allowlist entry. A live check confirmed the gate bites: adding
`:data:feed` to `sample/feed/shared` failed `isolationCheckFeedIos` with five unexpected projects
(`:core:common`, `:core:database`, `:core:network`, `:data:articlestore`, `:data:feed`) and the
SQLDelight native driver among the forbidden externals. The edit was reverted.

A missing configuration now fails loudly instead of silently skipping registration; a gate that
quietly stops running is worse than no gate, because the report still reports isolation.

The iOS gate resolves dependency metadata rather than invoking the Apple toolchain, so unlike the
link and Xcode gates it is valid on a Linux host.

Task and report names changed: `isolationCheck<Feature>` became
`isolationCheck<Feature>{Android,Ios}`, and `isolation-<feature>.txt` became
`isolation-<feature>-{android,ios}.txt`. The aggregate `isolationCheck` command is unchanged.

### Isolated Projects evaluated and deferred

Measured instead of assumed, so the "Remaining Risks" entry below is now a decision rather than an
open item. `help` with a warm configuration cache: 0.64 s median over three runs; with
`--no-configuration-cache`, configuring all 37 projects: 1.57 s. Configuration + task graph only
(`--dry-run --no-configuration-cache`): production 1.71 s, Feed sample 1.49 s, against 0.67 s for
the same sample on a cache hit.

The entire configuration phase is therefore about one second, and it is paid only when the
configuration cache misses — never during the ordinary edit-compile loop, where configuration is
skipped outright. That is the whole prize Isolated Projects competes for, on a 128-core host that
favours parallel configuration as much as any machine could.

Feasibility was confirmed separately: with `kmpa.architecture` temporarily disabled,
`:sample:feed:androidApp:assembleDebug` completed under `-Dorg.gradle.unsafe.isolated-projects=true`
with zero violations, so AGP 9.0.1, KGP 2.4.10, Compose Multiplatform and SQLDelight are compatible
and the root plugin is genuinely the only blocker. Restoring the plugin reproduced the documented
`Project ':' cannot access 'Project.configurations' ... via 'allprojects'` failure, confirming the
flag was active for both runs. `--warning-mode all` showed no Gradle 10 deprecation for
`allprojects`, `projectsEvaluated` or cross-project `configurations` access, so nothing forces the
migration on a schedule. Rationale and revisit conditions are in
`docs/03-isolated-projects.md`.

### CI removed, then restored

`.github/workflows/ci.yml` was originally deleted as a deliberate scope decision: the repository is
a sample application whose subject is the architecture, and at the time it was not hosted on GitHub.

That premise expired when the repository was published. `.github/workflows/gates.yml` (2026-08-15)
restores automatic enforcement on `ubuntu-latest`: `architectureCheck`, `isolationCheck`,
`buildLogicTest`, the Android unit tests, and the production and three sample Android assemblies,
with `build/reports/architecture/` uploaded as an artifact so the counts quoted in the README have a
source rather than a memory. The Linux host is not a compromise for the isolation gate specifically
— it resolves iOS dependency metadata rather than invoking the Apple toolchain — but framework
linking, the Xcode host builds and the iOS unit tests remain outside it and still require macOS.

One Gradle 10 deprecation remains and is not actionable here: "Using a Project object as a
dependency notation", raised from AGP internals
(`VariantDependenciesBuilder.build` via `VariantManager.createTestComponents`), not from repository
build scripts.

## Remaining Risks

- Isolated Projects is not enabled. This is now a measured decision rather than an open migration:
  the whole configuration phase is roughly one second and is skipped entirely on a configuration
  cache hit. The root architecture plugin remains the only compatibility blocker, and the migration
  design is retained for the day the trade changes.
- CI covers the structural gates but not the Apple toolchain. The Linux job runs both gates, the
  tests and the Android assemblies, and its iOS isolation half is genuine. Framework linking, the
  Xcode host builds and the iOS unit tests still depend on someone running them on macOS — on Linux
  those tasks are skipped while Gradle reports success, so their absence from CI is silent.
- Xcode 26.2 emits an `IDERunDestination` metadata warning for generic destinations even though all
  four builds succeed.
- Gradle reports one feature incompatible with Gradle 10. It was traced to AGP internals rather than
  to repository build scripts, so it clears with an AGP upgrade; track it, do not work around it.
- Benchmark wall time is one-machine evidence and contains warm-run variance. The resolved graph
  and executed-task reductions are the portable isolation evidence.

## Final Architecture Status

**READY FOR V1.0.** All required architecture, isolation, test, Android production, iOS production,
Android sample and iOS native sample build gates pass. Android runtime behavior was observed for
production and all three samples; the same production/sample flows were observed on iPhone 17 Pro
iOS 26.2.

The post-validation changes strengthen the isolation gate and narrow the repository's scope; neither
weakens a rule. Isolated Projects is closed as a measured decision rather than pending work. The
Android and iOS build/runtime evidence above still dates from the macOS run and was not re-verified
on the Linux host, so the next macOS session should re-run the full gate order before any release
claim.
