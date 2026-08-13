---
name: refactored-architecture
description: Inspect, build, migrate, or validate an Android+iOS Kotlin Multiplatform application whose primary goal is module-level build isolation — developing and running one feature without building the whole app — using feature-first Clean Architecture, Compose Multiplatform, reducer-based MVI with strongly typed one-shot effects, typed navigation, runtime Koin composition roots, per-feature fixtures, thin native hosts, convention plugins, machine-enforced dependency and isolation rules, and independently runnable feature samples on both platforms. Use for greenfield KMP setup, architecture migrations, new feature/module work, DI ownership cleanup, app/sample topology, architecture checks, CI isolation gates, build-isolation benchmarks, or KMP architecture reviews.
---

# Refactored KMP Architecture

Build or evolve an Android+iOS KMP application without assuming any repository-specific names or
that the project is greenfield. Preserve intentional working architecture, then make the smallest
changes needed to reach the rules below.

## Primary goal: module-level build isolation

Every rule below serves one measurable outcome: **a developer working on one feature builds and runs
only that feature, never the whole application.** Clean layering is the means, not the goal.

Splitting code into modules does not produce build isolation by itself. Four mechanisms do:

1. **The sample graph is a small subset of production.** A feature sample depends on the feature's
   presentation and domain plus deterministic fixtures — never on `data/*`. Excluding data keeps
   network, persistence, serialization, and vendor SDKs out of the daily compile.
2. **No feature depends on another feature.** Cross-feature decisions belong to `app/shared` only.
   One feature-to-feature edge collapses the isolation claim for both.
3. **`implementation` by default, so ABI-stable changes stop propagating.** A single unjustified
   `api` edge re-exports a dependency and re-compiles every consumer.
4. **No aggregating compile-time codegen in the dependency path.** DI frameworks that generate an
   aggregated component in the application module force that module to reprocess on every feature
   change, which is exactly the cost this architecture removes.

Treat the feature sample as the primary development loop, not an afterthought: a feature is not
finished until its sample runs on Android and iOS.

State the honest limits alongside the claim. Isolation does not reduce configuration time — more
modules cost more configuration — and it does not stop a shared design-system change from
recompiling the tree. Measure both instead of asserting either.

## Start with repository discovery

Before designing or editing:

1. Read repository instructions and inspect Git status.
2. Inspect `settings.gradle(.kts)`, root build files, version catalogs, included build logic, source
   sets, native hosts, CI, tests, and existing architecture checks.
3. Derive the project name, base package, application/bundle IDs, supported targets, module paths,
   Gradle/plugin versions, Xcode ownership, and feature boundaries from the repository.
4. Classify the request as greenfield, incremental migration, feature addition, or review.
5. Produce a short current-state dependency graph before proposing a target graph.

Never copy example names, package IDs, versions, SDK levels, signing values, or module paths into a
project without adapting and verifying them. Ask only when a material choice cannot be discovered.

## Architectural invariant

Use this direction:

```text
presentation/* ──► domain/* ◄── data/*
       │               │            │
       └──────────► core/* ◄─────────┘

app/shared ──► presentation/domain/core + selected shared data implementations
platform hosts ──► app/shared + selected platform implementations
fixtures/<feature> ──► only its own domain/<feature> (+ core contracts)
sample/<feature> ──► presentation/<feature> + domain/<feature> + fixtures/<feature>
```

Enforce these responsibilities:

| Area | Owns | Forbidden |
|---|---|---|
| `core/*` | Feature-neutral infrastructure and contracts | Feature/domain/data/presentation/app imports |
| `domain/*` | Pure business models, ports, use cases, rules | Compose, Koin, Android/UIKit, DTO/entity/SDK types |
| `data/*` | Domain port implementations, sources, DTO/entity mapping | Presentation, UI state, navigation |
| `presentation/*` | Shared UI, reducer MVI, UI mapping, ViewModel definitions | Data implementations, native controllers, repository lookup from composables |
| `fixtures/<feature>` | Deterministic implementations of that feature's domain/platform ports, fixed test data | Real I/O, other features, presentation, data, app, production credentials |
| `app/shared` | Root Compose surface, top-level typed graph, shared app composition, stable iOS API | Feature-owned business logic, native executable lifecycle |
| `app/android`, native iOS host | Startup, configuration, platform SDK integration | Duplicated screens, reducers, or business policy |
| `sample/<feature>` | Isolated executable development environment | `data/*`, app root, other features, production side-effect graphs |

These edges are forbidden without exception:

```text
domain       → data, presentation, app, sample, fixtures
presentation → data, app, sample
data         → presentation, app, sample
core         → domain, data, presentation, app, sample, fixtures
sample       → app, data, another feature
fixtures     → data, presentation, app, another feature
any feature  → any other feature (cross-feature belongs to app/shared)
```

`sample → data` is the load-bearing rule. It is what keeps network, persistence, serialization, and
vendor SDKs out of the feature development loop. Waive it only when persistence or a real
integration is the sample's explicit, documented purpose.

Reject cycles and umbrella modules used to bypass these rules.

Default every dependency to `implementation`. Use `api` only when a public type intentionally
appears in the module's own signatures, and record the reason; each `api` edge widens recompilation
for every consumer.

## Preferred topology

Use this greenfield shape, adapting names only when repository constraints justify it:

```text
app/
├── shared/                 # KMP app root and static iOS framework API
├── android/                # com.android.application
└── ios/                    # optional folder only if native host is colocated

core/<capability>/
domain/<feature>/
data/<feature>/
presentation/<feature>/
fixtures/<feature>/         # deterministic port implementations shared by tests and the sample

sample/<feature>/
├── shared/                 # sample root, typed graph, DI selection, iOS framework API
├── androidApp/             # com.android.application executable
└── iosApp/                 # Xcode-owned executable, or a central iosSamples target
```

`fixtures/<feature>` exists so a deterministic fake is written once and consumed by both that
feature's `commonTest` sources and its sample. It is feature-scoped by rule: only `presentation`,
`domain`, and `sample` projects of the same feature may depend on it. A shared cross-feature fake
module is exactly the umbrella dependency this architecture rejects.

Widely shared UI modules are the natural enemy of build isolation: everything depends on
`core:designsystem`, so every change to it recompiles the tree. Separate stable tokens and theme
from frequently edited components, and treat churn in that module as an architectural signal rather
than an inconvenience.

A centralized Xcode sample project with one executable target per feature is equivalent to separate
`iosApp` folders and avoids duplicating Swift boilerplate. A framework build alone is not an iOS
sample executable.

Sample graphs must consume collected typed Effects. If the production destination is intentionally
outside the isolated graph, use a sample-local typed placeholder with back navigation; never swallow
the Effect or import an unrelated production feature just to handle it.

Do not rename stable modules only for aesthetics. Rename when it clarifies ownership without adding
coupling, and update Gradle, Xcode, CI, scripts, signing paths, and generated project sources together.

Read [module-blueprints.md](references/module-blueprints.md) before creating or moving modules.

## Domain observable contracts

Expose `Flow<T>` by default for observable domain streams. Expose `StateFlow<T>` only when the
business contract intentionally guarantees a current value and callers require synchronous `.value`
access. Review each API individually; do not mechanically replace repository state streams.

Implementation hotness is not automatically domain semantics. A data implementation may use
`StateFlow` internally while satisfying a `Flow` domain contract. Keep `StateFlow<ScreenState>` in
presentation because current UI state is explicitly part of that contract.

## MVI and typed effects

Keep Action, Event, and Effect separate:

```text
UI Action → ViewModel orchestration ─┬→ Event.reduce(oldState) → new State
                                     └→ typed one-shot Effect → route graph
```

Use a typed MVI ViewModel. Name it for what it does (`MviViewModel`), not for its position in a
hierarchy — `Base*` names describe inheritance rather than responsibility:

```kotlin
abstract class MviViewModel<
    S : ScreenState,
    E : ScreenEvent<S>,
    F : ScreenEffect,
>(stateStore: StateStore<S, E>) : ViewModel(), StateStore<S, E> by stateStore {
    private val effectChannel = Channel<F>(Channel.BUFFERED)
    val effects: Flow<F> = effectChannel.receiveAsFlow()

    protected fun sendEffect(effect: F) {
        val result = effectChannel.trySend(effect)
        check(result.isSuccess) {
            "Effect transport rejected effect (closed=${result.isClosed}): $effect"
        }
    }

    override fun onCleared() {
        effectChannel.close()
        super.onCleared()
    }
}
```

- Make each ViewModel expose only its feature/screen effect type.
- Let route graphs own controllers and exhaustively handle their typed effects without casts.
- Keep reducers pure, synchronous, immutable, and safe for repeated invocation by atomic updates.
- Never store one-shot effects in replaying state containers.
- Define Effect delivery as in-process and non-durable. With this policy, an active buffered
  transport is expected to accept an Effect and rejection fails fast instead of silently dropping it.
- Bound the fail-fast policy explicitly. Sending after `onCleared()` is a lifecycle defect, so send
  Effects only from `viewModelScope`, which is cancelled before the transport closes. Prove that
  ordering with a teardown-race test; an unbounded `check` that can fire during normal teardown is a
  production crash, not a guardrail.
- Test consumed-no-replay behavior and closed-transport rejection.
- Never merge Action, Event, and Effect into a global event hierarchy.

Read [mvi-navigation-di.md](references/mvi-navigation-di.md) before changing screens, effects,
navigation, or Koin ownership.

## DI ownership

Prefer a runtime composition container (Koin) over compile-time DI codegen, and record the reason:
aggregating annotation processors build their component in the application module, so every feature
change reprocesses that module and every sample inherits the same cost. That directly defeats
module-level build isolation. The trade is deliberate — runtime graphs fail later than generated
ones — so it must be paid back with DI graph startup tests for every composition root.

Use the container only for composition:

- Domain contains constructors and ports but imports no DI framework.
- Data modules bind implementations they own.
- Presentation modules define ViewModels they own.
- Application/sample composition roots construct reusable cross-layer use cases and choose active
  data/platform implementations. Shared KMP implementations needed by an exported iOS framework may
  be selected in `app/shared`; native runtime implementations remain host-owned.
- Keep a use case near presentation only when it is genuinely presentation-specific and not a
  reusable application operation.
- Keep constructors directly callable in tests; never resolve repositories from composables.

Avoid a global DI dumping-ground. Group reusable UseCase definitions by capability, then let each
production/sample root select those coherent modules alongside data, presentation, and platform
modules. A composition root may select broadly because assembly is its purpose; individual definition
modules and infrastructure modules must remain narrow.

## Build and platform policy

- Default to `commonMain`; use platform source sets only for direct platform APIs/dependencies.
- Support Android and iOS targets already required by the project. Do not add desktop implicitly.
- Keep Android executables in `com.android.application` modules separate from KMP libraries.
- Export static KMP frameworks to thin Xcode/Swift executable hosts.
- Centralize repeated Gradle target/dependency configuration in convention plugins, but keep the
  included build small and stable: editing build logic invalidates configuration for every project.
- Declare dependencies as `implementation` unless a type is genuinely part of the module's own API.
- Preserve an existing compatible toolchain. For greenfield work, verify current official
  compatibility before pinning versions.
- Use interfaces plus DI for capabilities with configuration, lifecycle, dependencies, multiple
  implementations, or test doubles. Reserve `expect/actual` for small equivalent primitives.

Read [build-system.md](references/build-system.md) before changing Gradle or target configuration and
[platform-hosts-samples.md](references/platform-hosts-samples.md) before wiring hosts or samples.

## Implementation workflow

1. Inspect and map the current dependency/composition graph.
2. Identify exact violations or justified topology changes; do not redesign from scratch.
3. Write the intended module edges and composition ownership.
4. Implement bottom-up: core contract if needed, domain, fixtures, presentation, data, graph,
   composition roots. Build the sample before the production root — if the feature cannot run in
   isolation, the topology is already wrong.
5. Keep the repository buildable after each meaningful slice.
6. Add native executable wrappers for the sample on both platforms.
7. Add behavioral tests and executable architecture checks, including invalid-structure fixtures.
8. Validate the implementation before synchronizing architecture documentation.
9. Update documentation only with behavior proven by implementation/tests.
10. Update CI with separated production and isolated-sample gates.
11. Inspect real resolvable dependency graphs for at least two samples.
12. Build and, where practical, install/launch Android and iOS executables after documentation sync.
13. Benchmark the final architecture last, recording environment, cache configuration, repetitions,
    individual runs, and medians. Build isolation is this architecture's central claim, so the
    measurement is mandatory rather than optional — and reporting a modest or negative result is a
    successful outcome, not a failed one.

For offline-first products, keep local storage as the observable truth and synchronize remote data
into it. Never let probabilistic AI/network output override deterministic safety or authorization
rules.

## Executable architecture requirements

Prefer a lightweight Gradle verification plugin/task plus focused behavioral tests. Expose two
commands:

```bash
./gradlew architectureCheck   # declared edges, source rules, layer directions
./gradlew isolationCheck      # resolved sample graphs against an explicit allowlist
```

`isolationCheck` is what converts the build-isolation claim from prose into a gate. For every
sample, resolve a real compile/runtime classpath, collect the transitive `project :...` nodes, and
compare them to the allowlist declared for that sample. Fail on any extra project, and name it.
Folder shape and declared edges are not evidence; a resolved graph is. Keep the allowlist in the
repository next to the sample so widening it is a reviewable diff rather than a silent regression.

The check must inspect declared project/external dependencies and source-level platform/controller
imports where Gradle metadata is insufficient. Locate actual `override fun reduce(...)` bodies rather
than relying on `*Event.kt` or `*Reducer.kt` filenames. Add unit tests proving important forbidden and
allowed directions plus source-rule rejection fixtures. Static scanning is a guardrail, not a
semantic-purity proof; reducer behavior tests must complement it.

Read [architecture-verification.md](references/architecture-verification.md) before adding checks,
CI, isolation evidence, or benchmarks.

## Verification baseline

Discover actual task paths first, then run equivalents of:

```bash
./gradlew architectureCheck
./gradlew isolationCheck
./gradlew check
./gradlew :app:android:assembleDebug
./gradlew :app:shared:compileKotlinIosSimulatorArm64
```

For at least two isolated samples, build Android application and iOS shared framework, then build the
native Xcode sample schemes. When simulator/emulator access exists, install and launch them and inspect
visible fake data, state changes, effects, back behavior, DI startup, and resources.

## Review checklist

Report or fix:

- forbidden layer or platform dependencies;
- any feature-to-feature edge, and any `sample → data` or `sample → app` edge;
- unjustified `api` dependencies that widen recompilation;
- deterministic fakes duplicated between a feature's tests and its sample instead of living in
  `fixtures/<feature>`;
- aggregating compile-time DI codegen reintroduced into the feature build path;
- untyped/cast effects or replayed transient effects;
- reducers with I/O, time/random access, mutation, coroutine work, or navigation;
- presentation-owned reusable use cases;
- ViewModels retaining native controllers/navigators;
- feature dependencies on `app/shared`;
- samples importing broad production graphs, auth/network/storage/analytics/push implementations;
- duplicated feature UI/ViewModels/fakes across native hosts;
- framework-only iOS “verification” with no executable target;
- convention configuration copied across modules;
- untested DI graphs or dependency claims based only on folder names.

## Completion report

Report:

1. recommendation status: implemented, partial, rejected, or not applicable;
2. before/after contracts and topology;
3. final dependency graph and DI ownership;
4. machine-enforced rules, including the `isolationCheck` allowlist for each sample;
5. real isolation graphs for at least two features;
6. every verification command and result;
7. measured benchmarks with environment/cache disclosure when requested;
8. remaining runtime or architectural risks.

Never claim completion when a required native executable does not build, a known boundary remains
violated, or runtime-only behavior was inferred from compilation.
