# KMP Architect Sample App

A feature-isolated Kotlin Multiplatform architecture for Android and iOS: **a developer working on
one feature builds and runs only that feature, never the whole application — on both platforms.**

Per-feature demo apps are an old idea in large Android codebases. Two things are harder to find, and
they are what this repository is for: the same property **on both platforms** — one KMP codebase where
a feature builds and runs on its own on Android *and* iOS — and **a check that it still holds**, since
the architecture tools in this space read declared structure or source text and none of them resolves
what an iOS framework links.

The target is the repository several teams share: each team builds, runs and ships its own feature on
Android and iOS without the app root, without another team's data layer, and without waiting for
anyone else's build.

Clean layering, MVI and typed navigation are here as means, not as the goal. The goal is a smaller
development loop, and the repository is built so that claim can be checked by a machine rather than
believed from a diagram.

The architecture was developed in a private codebase of **62 Gradle modules across 11 features**.
This repository is a three-feature reference implementation extracted from it — small enough to read
in an afternoon, complete enough to run the same gates on both platforms. Every number below comes
from *this* repository and is reproducible here.

**The write-up** — three articles on why the topology is shaped this way, how the build proves it on
both platforms, and what it takes to make MVI rules enforceable instead of aspirational:

1. [I Stopped Building My App to Work on It](https://medium.com/@kilcimurat776/your-feature-should-not-need-your-app-to-build-9a7115b00fca)
   — your feature should not need your app to build: build isolation as the goal, layering as the
   means, and the numbers that say whether it worked
2. [Folder Shape Is Not Evidence](https://medium.com/@kilcimurat776/folder-shape-is-not-evidence-25f2805b45f1)
   — making a Gradle build prove that a KMP feature is isolated, on Android *and* iOS
3. [Your Navigation Bug Has a Delay Fuse](https://medium.com/@kilcimurat776/auditable-mvi-1862a1430931)
   — auditable MVI: separating Action, Event and Effect, and making the separation a build failure
   instead of a code-review habit

All three also read as one set, with the figures inline, at
[kilcimurat.github.io/kmp-architect-sample-app](https://kilcimurat.github.io/kmp-architect-sample-app/)
(source in [`docs/`](docs/)).

## The claim, measured

Editing a line in a feature's `data` layer and rebuilding, on the same machine:

| Scenario | Production app | Feed sample | Bookmarks sample |
|---|---:|---:|---:|
| cold build | 236 tasks | 125 | 125 |
| no change | 20 tasks | 9 | 9 |
| presentation edit | 24 tasks | 13 | 13 |
| **data edit** | **24 tasks** | **9 — unchanged** | **9 — unchanged** |
| design-system edit | 24 tasks | 13 | 13 |

The bold row is the point. A change in the production data layer leaves both feature samples at
their no-change graph: nothing recompiles, because a sample is structurally forbidden from depending
on `data/*`. That single rule is what keeps the network client, the database driver, serialization
and DTO mapping out of the daily feature loop.

Method, host and raw repetitions: [docs/02-benchmarks.md](docs/02-benchmarks.md). Task count is the
portable signal; wall time is machine-specific and is reported without being dressed up — one sample
scenario was *slower* than production despite running half the tasks, and that is left in the table.

Two honest limits, stated up front: isolation does not reduce Gradle configuration time (more
modules cost more configuration), and it does not stop a shared design-system change from
recompiling the tree. Both are measured rather than argued away.

## The two gates

Folder shape is not evidence of isolation. A resolved dependency graph is.

```bash
./gradlew architectureCheck   # declared edges, external dependencies, source-level rules
./gradlew isolationCheck      # every sample's resolved graph vs. a checked-in allowlist
```

`architectureCheck` currently inspects 211 declared project edges, 834 external edges and 85
production sources — the counts printed by the task itself into
`build/reports/architecture/architecture-check.txt`, and by the CI job on every push. It rejects
forbidden layer directions, feature-to-feature edges, `api` exposure
that is not justified in `config/api-allowlist.txt`, fixtures consumed by anything but their own
feature's tests and sample, ViewModels retaining native controllers, repository lookup from
composables, and effects stored in replaying state. Every rule has a rejection fixture beside it in
`build-logic`; a rule without one is treated as unproven.

`isolationCheck` runs **once per sample per platform** — six checks for three features. The Android
graph comes from the executable's `debugRuntimeClasspath`, the iOS graph from the framework module's
`iosSimulatorArm64CompileKlibraries`. Checking only Android would say nothing about what the static
framework links.

| Sample | Android graph | iOS graph |
|---|---:|---:|
| Feed | 9 projects | 8 |
| Article | 10 projects | 9 |
| Bookmarks | 9 projects | 8 |

The difference is the Android executable host; the projects beneath it are identical on both
platforms, which one shared allowlist per sample now asserts instead of assuming. Adding
`:data:feed` to the Feed sample fails the gate by name:

```text
isolationCheck FAILED
  ios: :sample:feed:shared (iosSimulatorArm64CompileKlibraries)
    unexpected project on the resolved graph: :core:common
    unexpected project on the resolved graph: :core:database
    unexpected project on the resolved graph: :core:network
    unexpected project on the resolved graph: :data:articlestore
    unexpected project on the resolved graph: :data:feed
    forbidden external dependency reached the sample: app.cash.sqldelight:native-driver
    ... 37 more, the rest of SQLDelight and Ktor and the JSON parser
  allowlist: sample/feed/isolation-allowlist.txt
```

Widening a sample is therefore a reviewable diff to `sample/<feature>/isolation-allowlist.txt`, not a
regression that surfaces months later as a slower build.

## What the app is

An offline-first article reader with three features — `feed`, `article`, `bookmarks` — across 30
modules. A Ktor client syncs into a SQLDelight store and local storage is the single observable
truth. The demo backend is a `MockEngine` selected in `app/shared`, so content negotiation, DTO
parsing and sync are all real code while results stay reproducible.

Each feature has its own runnable sample application on **both** platforms. A feature is not
finished until its sample runs on Android and iOS.

## What that looks like from the IDE

<img src="docs/images/run-configurations.png" alt="Eight run configurations in the IDE: four Android, four Apple" width="330">

Eight run configurations: the production app and one per feature, on each platform. A developer
working on bookmarks picks `sample.bookmarks.androidApp` or `BookmarksSample` and never builds the
other two features, the app root, or the data layer.

### Production

![The production app running on Android and iOS](docs/images/production-app.png)

Four articles from the demo backend, and the `Feed` / `Saved` tab bar. The tab bar exists only here:
deciding that two features share a navigation surface is a cross-feature decision, and `app/shared`
is the only module entitled to make one.

### The same feature, in its isolated sample

![The feed sample running on Android and iOS](docs/images/sample-feed.png)

Three deterministic articles from `fixtures/feed`, and no tab bar — because the sample's graph
contains one feature. Same presentation code as the screen above; different composition root.

![The article sample running on Android and iOS](docs/images/sample-article.png)

![The bookmarks sample running on Android and iOS](docs/images/sample-bookmarks.png)

The article and bookmarks samples, each on both platforms. Every screenshot in this section is the
same build a developer runs during the day — not a demo mode inside the production app.

## Rules that produce the isolation

```text
presentation/* ──► domain/* ◄── data/*
       │               │            │
       └──────────► core/* ◄─────────┘

app/shared ──► presentation/domain/core + selected shared data implementations
platform hosts ──► app/shared
fixtures/<feature> ──► only its own domain/<feature>
sample/<feature> ──► presentation + domain + fixtures of that feature only
```

- **`sample → data` is forbidden.** The load-bearing rule; everything above is downstream of it.
- **No feature depends on another feature.** Cross-feature decisions belong to `app/shared` alone. This
  is the team boundary as much as the module boundary: one team cannot couple itself to another team's
  code without failing the build by name, on Android and on iOS.
- **`implementation` by default.** Every `api` edge needs a recorded justification, because one
  unjustified re-export restores the recompilation cascade the module split was meant to remove.
- **No aggregating DI codegen in the feature build path.** This is why the container is runtime
  (Koin) — and why every composition root owes a graph-startup test.
- **`domain/*` is pure Kotlin.** No Compose, no Koin, no Android/UIKit, no DTOs.
- **MVI keeps Action, Event and Effect separate.** Reducers are pure and synchronous; effects are
  typed, one-shot and channel-delivered, never stored in replaying state.
- **Deterministic fakes live in `fixtures/<feature>`**, written once and consumed by both that
  feature's presentation tests and its sample — never duplicated between them.

These rules are stricter than three features require. They were written for eleven, and the reference
keeps them intact rather than relaxing them for a smaller demo.

## Repository shape

```text
build-logic/                 convention plugins + architecture rules (with their own unit tests)
app/{shared,android}         root graph, cross-feature routing, thin Android host
core/*                       mvi, navigation, common, model, designsystem, ui, network,
                             database, sharing
domain/{feed,article,bookmarks}        pure Kotlin
data/{feed,article,bookmarks}          + data/articlestore
fixtures/{feed,article,bookmarks}      deterministic fakes, shared by tests and samples
presentation/{feed,article,bookmarks}
sample/{feed,article,bookmarks}/{shared,androidApp}   + isolation-allowlist.txt each
iosApp/ iosSamples/          XcodeGen specs; generated projects are git-ignored
```

The authoritative specification of these rules is
[`.claude/skills/refactored-architecture/`](.claude/skills/refactored-architecture/) — the SKILL and
its five references. The repository is the proof; that directory is the contract.

## Building

The daily loop is one command:

```bash
./gradlew :sample:feed:androidApp:installDebug     # also :article, :bookmarks
```

Everything else:

```bash
./gradlew architectureCheck isolationCheck buildLogicTest   # the gates
./gradlew check                                             # all module tests
./gradlew :app:android:assembleDebug                        # production Android
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64   # production iOS framework
```

Requires JDK 21 for the Gradle daemon (pinned via `gradle/gradle-daemon-jvm.properties`), Android
SDK 36, and Xcode for anything iOS. Versions live in `gradle/libs.versions.toml`; add dependencies
there rather than inline.

## iOS

Xcode projects are generated from reviewable XcodeGen specs rather than hand-edited:

```bash
./scripts/generate-xcode-projects.sh          # needs `brew install xcodegen`
open KmpArchitect.xcworkspace                 # both projects, all four schemes
open iosApp/KmpArchitectSampleApp.xcodeproj
open iosSamples/KmpArchitectSamples.xcodeproj
```

`KmpArchitect.xcworkspace` is hand-written (nine lines of `contents.xcworkspacedata`, no generated
UUIDs) and only references the two generated projects. Production and samples stay separate
projects — the workspace exists because an IDE binds to a single Xcode file, and binding it to a
project would hide whichever three schemes are not in it.

Production scheme: `KmpArchitectSampleApp`. Sample schemes: `FeedSample`, `ArticleSample`,
`BookmarksSample`.

Build, install and launch one of them on a simulator without opening Xcode — the iOS counterpart of
`:sample:feed:androidApp:installDebug`:

```bash
./scripts/run-ios-simulator.sh FeedSample     # also ArticleSample, BookmarksSample,
                                              # KmpArchitectSampleApp
```

In Android Studio the KMP plugin derives its own iOS run configurations from the single Xcode file
recorded in `.idea/xcode.xml`; pointing that at `KmpArchitect.xcworkspace` gives all four schemes
instead of only the production one.

Three of them are also checked in under `.idea/runConfigurations/` — `FeedSample`, `ArticleSample`
and `BookmarksSample`, each bound to its Xcode scheme, so a fresh clone lists the samples without
anyone configuring them. `KmpArchitectSampleApp` comes from the workspace itself. These are native
Apple run configurations and need the IDE's Apple support; on a host without it, the script above is
the equivalent.

Note for non-macOS hosts: Apple link and test tasks are silently **skipped** on Linux while Gradle
still reports `BUILD SUCCESSFUL`. Treat a green Linux build as evidence of nothing on iOS. The iOS
isolation gate is the one exception — it resolves dependency metadata rather than invoking the Apple
toolchain, so it is valid everywhere.

## Using this in your own project

The repository is a reference you can lift from, not a library you depend on. Nothing here is
published to a repository server: you copy the parts that are generic and write the parts that
describe your product.

**Copy as is** — `build-logic/` (convention plugins, the rule set and its tests), `core/mvi`,
`core/navigation`, and the two scripts in `scripts/`.
**Write for yourself** — `config/api-allowlist.txt`, `config/infrastructure-modules.txt`, every
`sample/<feature>/isolation-allowlist.txt`, and the module list in `settings.gradle.kts`. Those four
describe *your* topology; a copied allowlist is a lie about your graph.

### Greenfield, in the order that works

1. **Take `build-logic/` and include it.** In `settings.gradle.kts`, inside `pluginManagement`, add
   `includeBuild("build-logic")`. It is an included build, so nothing is published and its own tests
   run with `./gradlew buildLogicTest`.
2. **Apply the gate at the root.** `id("kmpa.architecture")` in the root `build.gradle.kts` is what
   registers `architectureCheck` and the per-sample `isolationCheck` tasks.
3. **Create the two config files** — `config/api-allowlist.txt` and
   `config/infrastructure-modules.txt`. Start them empty except for the header comment; the gate
   tells you what you owe a justification for.
4. **Lay out one feature before you lay out five.** `domain/<feature>` and `data/<feature>` and
   `fixtures/<feature>` use `kmpa.kmp.library`; `presentation/<feature>` uses `kmpa.kmp.compose`.
   Get the gate green on one feature — the rules teach faster than any document.
5. **Add the sample.** `sample/<feature>/shared` uses `kmpa.kmp.framework`,
   `sample/<feature>/androidApp` uses `kmpa.android.app`, and
   `sample/<feature>/isolation-allowlist.txt` lists exactly the projects that sample may reach.
   Run `./gradlew isolationCheck` and let it correct you.
6. **Then the app root.** `app/shared` (`kmpa.kmp.framework`) is the only module allowed to know
   that two features exist; `app/android` (`kmpa.android.app`) is a thin host.
7. **Then iOS.** See below — the Xcode side is generated, not hand-edited.

### Adding a feature, once the shape exists

A feature costs five modules and three registrations. In order:

```text
domain/<feature>         kmpa.kmp.library    ports + use cases, pure Kotlin
data/<feature>           kmpa.kmp.library    implementations; nothing else may depend on it
fixtures/<feature>       kmpa.kmp.library    deterministic fakes for the ports
presentation/<feature>   kmpa.kmp.compose    ViewModel, route graph, screens
sample/<feature>/…       framework + android app + isolation-allowlist.txt
```

Then: add the projects to `settings.gradle.kts`, add the sample target to `iosSamples/project.yml`,
and (optionally) check in an IDE run configuration under `.idea/runConfigurations/`. Finally run
`./gradlew architectureCheck isolationCheck` — a new feature that compiles but breaks a boundary
fails here, by name, before anyone reviews it.

### Migrating an existing codebase

Do not start by splitting modules. Start by making the boundary visible:

1. Add `build-logic/` and the root plugin, and run `architectureCheck` on the codebase as it is.
   The first run is a list of everything the architecture already violates. Do not fix it yet.
2. Pick the feature you edit most. Extract its `domain` and `fixtures` first — those have no
   dependencies to unwind — then its `presentation`.
3. Give that one feature a sample and an allowlist. This is the moment the daily loop changes, and
   it is worth doing before the second feature.
4. Only then move `data`, and let the `sample → data` rule tell you what you missed.
5. Keep the gates in CI from day one, so the boundary you just paid for cannot regress while you
   migrate the next feature.

### iOS setup

Xcode projects are generated from reviewable specs; you never hand-edit a `.xcodeproj`.

```bash
brew install xcodegen
./scripts/generate-xcode-projects.sh              # from iosApp/project.yml and iosSamples/project.yml
./scripts/run-ios-simulator.sh FeedSample         # build, install and launch without opening Xcode
```

Adding a feature means adding one target to `iosSamples/project.yml`, which shares the Swift host
code with the other samples. Point Android Studio's `.idea/xcode.xml` at `KmpArchitect.xcworkspace`
to get all four schemes instead of only the production one.

### The specification is executable

The authoritative definition of these rules is
[`.claude/skills/refactored-architecture/`](.claude/skills/refactored-architecture/) — a SKILL file
and five references (module blueprints, build system, platform hosts and samples, MVI/navigation/DI,
and architecture verification), about 1,900 lines in total. It is written to be applied, not only
read: point an agent such as Claude Code at that directory and it can set the topology up in your
repository, or review an existing one against it. The repository is the proof that the specification
produces something that builds; that directory is the specification itself.

## Evidence

Nothing in this repository claims a result that was not run.

- [ARCHITECTURE_VALIDATION_REPORT.md](ARCHITECTURE_VALIDATION_REPORT.md) — every gate, command and
  result, including the seven failures found during validation and how each was fixed
- [docs/00-toolchain.md](docs/00-toolchain.md) — version constraints discovered by building, not by
  reading release notes
- [docs/01-ios.md](docs/01-ios.md) — macOS framework and Xcode host evidence, and its limits
- [docs/02-benchmarks.md](docs/02-benchmarks.md) — build-isolation measurements with environment,
  cache state and repetitions
- [docs/03-isolated-projects.md](docs/03-isolated-projects.md) — why Gradle Isolated Projects is
  *not* enabled, measured rather than assumed, plus the migration design kept for the day it matters
- `build/reports/architecture/` — generated edge and resolved-graph reports
- `build/benchmark-results.tsv` — raw benchmark repetitions

Both gates run in CI on every push and pull request
([`.github/workflows/gates.yml`](.github/workflows/gates.yml)), on a **Linux** runner — which is
only possible because of the property described above: `isolationCheck` resolves iOS dependency
metadata instead of invoking the Apple toolchain, so its iOS half is as valid there as on a Mac.
Framework linking, the Xcode host builds and the iOS unit tests are not covered by that job; those
need macOS or a local run. Run the two commands above before trusting any structural change.

## License

[Apache License 2.0](LICENSE).
