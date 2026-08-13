# CODEX_HANDOFF

> **Status: historical. Superseded — do not read this as a description of this repository.**
>
> This was a one-time briefing written to move architectural context out of a chat transcript and
> into the repository. It has served that purpose: the work it asked for was implemented and
> validated, and the open items in §37 are closed.
>
> Read instead, in this order:
>
> 1. `.claude/skills/refactored-architecture/` — the authoritative specification
> 2. `ARCHITECTURE_VALIDATION_REPORT.md` — what was actually run, and with what result
> 3. `CLAUDE.md` — the working guide for this repository
>
> Two warnings if you read on. The examples use feature names from a **different** project
> (`Kitchen`, `Cook`, `Pantrio`); this repository's features are `feed`, `article` and `bookmarks`.
> And the numbers in §27–29 — the 50/12/12 dependency counts and the M2 Max benchmark table —
> belong to that other project and were never true here. The current measurements are 9/10/9 on
> Android and 8/9/8 on iOS, in the validation report.
>
> It is kept because the reasoning behind several decisions is recorded here more fully than
> anywhere else, and because deleting the record of how a design was argued is a poor trade for a
> tidier root directory.

## Purpose

This file transfers the architectural context and decisions from a long ChatGPT discussion into Codex so work can continue inside the repository without replaying the entire conversation.

The project is a Kotlin Multiplatform (KMP) Android + iOS application architecture intended for real production use, not a toy template.

The architecture's **primary goal is module-level build isolation**:

> A developer working on one feature should be able to build, run, test, and iterate on that feature without building the whole application or unrelated feature/data graphs.

Clean Architecture, MVI, samples, fixtures, dependency rules, and DI choices are means to achieve that goal.

---

# 1. Source-of-truth order

When there is a conflict, use this priority:

1. **Actual repository implementation**
2. **Passing tests and machine-enforced architecture/isolation checks**
3. **Latest architecture SKILL and its reference files**
4. **ARCHITECTURE_VALIDATION_REPORT.md**
5. This handoff document
6. Historical/older architecture decisions

Do not force the implementation to match stale prose.

Do not assume a rule is implemented merely because it appears in the latest SKILL.

The repository implementation, resolved Gradle graphs, executable builds, and tests are the ultimate source of truth.

---

# 2. Files to read first

Before changing anything, locate and read the repository equivalents of:

```text
AGENTS.md                                  # if present
CODEX_HANDOFF.md                           # this file
ARCHITECTURE_VALIDATION_REPORT.md          # latest validation evidence

.claude/skills/refactored-architecture/
├── SKILL.md
└── references/
    ├── architecture-verification.md
    ├── build-system.md
    ├── module-blueprints.md
    ├── mvi-navigation-di.md
    └── platform-hosts-samples.md
```

Also inspect:

```text
settings.gradle.kts
gradle/libs.versions.toml
build-logic/
app/
iosApp/
core/
domain/
data/
presentation/
fixtures/
sample/
.github/workflows/
```

Use actual repository paths. Do not blindly assume these exact paths exist.

---

# 3. Current architectural intent

## Primary invariant

```text
presentation/<feature> ───────► domain/<feature> ◄─────── data/<feature>
          │                          │                         │
          └────────────────────► core/* ◄─────────────────────┘

app/shared
    ├── root Compose App
    ├── top-level typed navigation
    ├── cross-feature composition
    ├── reusable use-case composition
    └── stable native-facing iOS API

platform hosts
    ├── Android executable lifecycle/platform adapters
    └── iOS executable lifecycle/platform adapters

fixtures/<feature>
    └── deterministic implementations of that feature's domain/platform ports

sample/<feature>
    ├── feature presentation
    ├── feature domain
    ├── feature fixtures
    ├── required core modules
    └── NO production data/app/unrelated feature graph
```

---

# 4. Main architectural goal: feature build isolation

The architecture is deliberately optimized for this workflow:

```text
Developer edits Kitchen feature
        ↓
sample/kitchen
        ↓
presentation/kitchen
        ↓
domain/inventory
        ↓
fixtures/inventory
        ↓
required core modules only
```

The following should **not** be required for the normal feature development loop:

```text
app/shared
full Android application
unrelated features
production network stack
production database stack
production serialization stack
production auth
production push
production analytics
unrelated SDKs
```

A folder structure alone does not prove isolation.

Isolation must be proven from the **resolved dependency/task graph**.

---

# 5. Non-negotiable dependency rules

The current intended rules are:

```text
domain       -> NO data, presentation, app, sample, fixtures
presentation -> NO data, app, sample in production/main sources
data         -> NO presentation, app, sample
core         -> NO feature/domain/data/presentation/app/sample/fixtures
fixtures     -> NO data, presentation, app, another feature
sample       -> NO app, data, another feature
feature A    -> NO feature B
```

Cross-feature decisions belong to `app/shared`.

### Critical rule

```text
sample -> data
```

is forbidden for normal feature samples.

This is one of the most important rules in the architecture because excluding `data/*` keeps network, persistence, serialization, DTO mapping, and vendor SDKs out of the daily feature compile path.

An integration-focused sample may intentionally waive this, but the waiver must be explicit and reviewable.

---

# 6. `fixtures/<feature>` intent

The newest architectural direction introduces:

```text
fixtures/<feature>
```

for deterministic fakes and fixed sample/test data.

Its purpose is to avoid duplicating the same fake implementation between:

```text
presentation/<feature> tests
sample/<feature>
```

A fixtures module should normally:

```text
depend on:
    domain/<feature>
    narrowly required core contracts

not depend on:
    data/*
    presentation/*
    app/*
    another feature
    real I/O
    production credentials
```

## Intended consumers

The intended consumer rule is:

```text
fixtures/<feature> may be consumed by:

    presentation/<feature> TEST source sets only
    sample/<feature>/*

and by nothing else.
```

Domain tests should stub their own ports inline.

Do not add:

```text
domain/<feature> -> fixtures/<feature>
```

because `fixtures` already depends on domain and this would create a project cycle.

Create a dedicated fixtures module only when it has at least two real consumers. If a fake is used by only one sample, keep it in that sample instead of paying another Gradle configuration cost.

---

# 7. Known documentation inconsistency to fix

The latest SKILL currently contains a wording contradiction around fixtures.

One section correctly says:

```text
fixtures/<feature> may be consumed only by:
- presentation/<feature> test source sets
- sample/<feature>
```

but another description says that `presentation`, `domain`, and `sample` projects of the feature may depend on it.

The `domain` part is wrong and should be removed.

The intended rule is:

```text
presentation test sources + sample only
```

Do not let production presentation, app, data, domain, or another feature consume fixtures.

---

# 8. MVI model

The architecture uses reducer-based MVI with three deliberately separate concepts:

```text
Action
Event
Effect
```

## Meaning

```text
Action
    UI/user intent

Event
    pure state transition

Effect
    one-shot external request
    e.g. navigation, back, snackbar, native/external action
```

Flow:

```text
UI Action
    ↓
ViewModel orchestration
    ├── Event.reduce(oldState) -> new State
    └── typed Effect -> route graph -> navigation/native behavior
```

---

# 9. Event-owned reducer

The reducer lives inside the Event:

```kotlin
interface ScreenEvent<S : ScreenState> {
    fun reduce(oldState: S): S
}
```

StateStore applies it atomically:

```kotlin
mutableState.update { current ->
    event.reduce(current)
}
```

Reducers must remain:

```text
pure
synchronous
deterministic for their inputs
immutable with respect to old state
safe for repeated invocation
```

Reducers must not:

```text
launch coroutines
call repositories
call data sources
perform I/O
emit Effects
navigate
emit analytics
use service locators
read implicit clocks/randomness
mutate collections owned by oldState
```

`MutableStateFlow.update` may invoke the lambda more than once under contention, which is why reducer purity is non-negotiable.

---

# 10. Typed MVI ViewModel

The latest naming direction is:

```kotlin
MviViewModel<S, E, F>
```

instead of a generic inheritance-oriented `BasicViewModel`.

Conceptually:

```kotlin
abstract class MviViewModel<
    S : ScreenState,
    E : ScreenEvent<S>,
    F : ScreenEffect,
>(
    stateStore: StateStore<S, E>,
) : ViewModel(), StateStore<S, E> by stateStore {

    private val effectChannel = Channel<F>(Channel.BUFFERED)

    val effects: Flow<F> = effectChannel.receiveAsFlow()

    protected fun sendEffect(effect: F) {
        val result = effectChannel.trySend(effect)
        check(result.isSuccess) {
            "Effect transport rejected effect (closed=${result.isClosed}): $effect"
        }
    }
}
```

Every feature/screen exposes its own typed Effect hierarchy.

Avoid:

```text
Flow<ScreenEffect>
casts in graph handlers
global Event hierarchies
global Effect hierarchies
MutableStateFlow<Effect?>
global event bus
```

---

# 11. Effect delivery policy

The established policy is:

```text
transport:
    Channel<F>(Channel.BUFFERED)

exposed as:
    Flow<F>

replay:
    none after consumption

durability:
    in-process only

process death:
    no delivery guarantee

failure:
    rejected sends are not silently ignored
```

Business state must be persisted as business state.

Navigation commands must not be persisted merely to make Effect delivery durable.

---

# 12. Open Effect teardown wording issue

The latest documentation contains a rule equivalent to:

> Emit Effects exclusively from `viewModelScope`.

That wording is too strict and conflicts with normal synchronous calls such as:

```kotlin
fun onAction(action: Action) {
    sendEffect(...)
}
```

The more accurate intended rule is:

```text
Synchronous Effects may be emitted directly from ViewModel-owned methods.

Any asynchronous Effect emission must originate from viewModelScope
or another ViewModel-owned scope that is cancelled before Effect
transport teardown.

Effects must never originate from an external/unowned scope that can
outlive the ViewModel.
```

Review the actual ViewModel lifecycle implementation before changing anything.

The purpose of the rule is to prevent an ordinary teardown race from turning the fail-fast Effect policy into a production crash.

Add/retain a test that proves normal ViewModel teardown cannot emit into an already-closed Effect channel.

---

# 13. Navigation ownership

ViewModels must not retain:

```text
NavController
Activity
UIViewController
native navigator
route graph object
```

The route graph owns navigation.

Typical flow:

```text
ViewModel
    ↓ typed Effect
Route graph
    ↓
NavController / platform navigation
```

Use typed routes and stable identifiers.

Do not pass repositories, large mutable objects, native controllers, or SDK objects through route arguments.

---

# 14. Sample navigation rule

Feature samples must consume the same typed Effects as production.

Do not do:

```kotlin
HandleEffects(viewModel.effects) { }
```

If a production Effect targets a feature intentionally outside the isolated sample graph, use a **sample-local typed placeholder destination**.

Example:

```text
Kitchen
    ↓ OpenFoodDetails(id)
SampleFoodDetailsPlaceholder(id)
    ↓ Back
Kitchen
```

Do not import another production feature merely to make the sample navigation work.

---

# 15. Domain Flow vs StateFlow rule

Default Domain observable contract:

```text
Flow<T>
```

Use:

```text
StateFlow<T>
```

only when the business contract intentionally guarantees:

```text
an always-available current value
synchronous `.value` access
hot-state ownership as part of the API semantics
```

Do not mechanically replace every `StateFlow`.

Presentation screen state should remain:

```text
StateFlow<ScreenState>
```

because current UI state is explicitly part of that contract.

A Data implementation may internally use `StateFlow` while exposing it through a Domain `Flow` contract.

---

# 16. DI ownership

Koin is currently used as runtime composition.

Rules:

```text
Domain
    no Koin imports

Data
    owns bindings for implementations it provides

Presentation
    owns ViewModel definitions

Composition roots
    construct reusable cross-layer UseCases
    select active data/platform implementations
```

Reusable UseCases should not be conceptually owned by Presentation.

Keep UseCase definitions grouped by coherent capability rather than one giant application DI dumping ground.

Example:

```text
inventoryUseCaseModule
recipeUseCaseModule
captureUseCaseModule
subscriptionUseCaseModule
```

The root selects these modules.

---

# 17. DI decision must remain behavior-based

Do **not** treat all compile-time DI as architecturally forbidden.

The architecture currently validates runtime Koin because it preserves the tested feature isolation characteristics.

The correct decision rule is:

```text
Reject DI/code-generation strategies that introduce aggregating work
into app/* or otherwise cause a leaf-feature edit to reprocess
unrelated modules.

Accept/reject a DI strategy based on the measured Gradle/task graph
and incremental behavior, not solely on whether it is runtime or
compile-time.
```

If a different DI system is proposed later:

1. implement a small pilot;
2. inspect the real task graph;
3. edit a leaf feature;
4. verify whether unrelated `app/*` or features reprocess;
5. benchmark;
6. decide from evidence.

---

# 18. app/shared responsibility

`app/shared` is an application root, not a dumping ground for generic shared code.

It may own:

```text
root App()
theme/root surface
top-level typed navigation
feature graph composition
cross-feature navigation decisions
reusable UseCase composition
shared KMP implementation selection where appropriate
stable exported iOS factory/API
```

Feature modules must never depend back on `app/shared`.

Native hosts own executable lifecycle and native runtime objects.

---

# 19. Platform hosts

## Android host may own

```text
Application
Activity
manifest
application ID
permissions
native service config
Android SDK bootstrap
Activity-dependent adapters
Android runtime implementation selection
```

## iOS host may own

```text
SwiftUI/UIKit app lifecycle
bundle configuration
Info.plist
entitlements
native service files
iOS SDK bootstrap
native implementation selection
```

Native hosts must not duplicate:

```text
business rules
reducers
repositories
shared Compose screens
feature state machines
```

Swift should call a narrow exported Kotlin entry point rather than manually assembling arbitrary internal Kotlin graphs.

---

# 20. Feature sample topology

Preferred shape:

```text
sample/<feature>/
├── shared/
│   ├── sample root
│   ├── typed graph
│   ├── use-case composition
│   ├── fixture selection
│   └── iOS framework factory
├── androidApp/
├── iosApp/                      # optional per feature
└── isolation-allowlist.txt
```

A centralized:

```text
iosSamples/
```

project with one executable scheme/target per feature is also acceptable and often preferable when native wrappers are identical.

A KMP framework build alone is **not** an iOS sample executable verification.

---

# 21. Fixtures vs sample ownership

Latest intended split:

```text
fixtures/<feature>
    deterministic reusable fake implementations
    fixed/seeded data

sample/<feature>/shared
    chooses fixtures
    builds sample DI graph
    owns sample navigation
    owns sample root
```

This avoids having one fake in presentation tests and another drifting fake in the sample.

Do not create a broad:

```text
sample:shared
fakes
testing-all-features
```

module that binds many feature implementations.

That recreates a miniature full application graph and destroys isolation.

Feature-neutral test helpers may live in a focused core testing module.

---

# 22. `implementation` vs `api`

Default project dependencies to:

```text
implementation
```

Use:

```text
api
```

only when a dependency's public type intentionally appears in the module API and therefore must be re-exported.

Unjustified `api` edges widen ABI propagation and can restore recompilation cascades even when module boundaries look correct.

The architecture should machine-check unjustified `api` declarations through a recorded allowlist or equivalent mechanism.

---

# 23. architectureCheck

`architectureCheck` is intended to validate:

```text
declared project dependency directions
forbidden external dependencies
source-level platform/controller rules
replaying Effect storage
actual ScreenEvent.reduce bodies
unjustified api dependencies
fixture consumer constraints
feature-to-feature edges
```

It should be backed by positive and negative fixture tests.

Required rejected examples include at least:

```text
domain -> data
domain -> presentation
presentation -> data
core -> feature
sample -> app
sample -> data
feature -> another feature
fixtures -> data
app -> fixtures
presentation MAIN -> fixtures
unjustified api
Domain -> Koin/Compose/Android/UIKit
Presentation -> NavController/native controller
MutableStateFlow<Effect?>
reducer side effects
```

Architecture scanning is a guardrail, not a mathematical proof of semantic purity.

Behavioral tests and code review remain necessary.

---

# 24. isolationCheck

The newer architecture direction separates isolation validation from architecture validation.

Commands conceptually:

```bash
./gradlew architectureCheck
./gradlew isolationCheck
```

`architectureCheck`:
- declared structure/source rules

`isolationCheck`:
- resolved feature sample graphs

Each sample should own a checked-in allowlist:

```text
sample/<feature>/isolation-allowlist.txt
```

Example:

```text
:presentation:<feature>
:domain:<feature>
:fixtures:<feature>
:core:mvi
:core:designsystem
:core:navigation
...
```

`isolationCheck` should:

1. resolve real compile/runtime configurations;
2. collect transitive project dependencies;
3. fail on unexpected projects;
4. name the unexpected project;
5. report stale allowlist entries that no longer resolve.

This makes widening the sample graph a reviewable Git diff rather than an invisible regression.

---

# 25. External dependency isolation

Project-node counting is not enough.

At least one representative sample should also prove that its resolved graph does not transitively contain unwanted external artifacts such as:

```text
network clients
database/persistence drivers
serialization implementations
production vendor SDKs
auth/push SDKs
```

A re-exported external dependency can break isolation without adding a new `project :...` node.

---

# 26. CI gates

Preferred CI separation:

```text
architecture-check
isolation-check
common/domain/presentation tests
android-production-build
ios-production-executable
android-samples matrix
ios-samples native executable matrix
```

Run `isolationCheck` early because its purpose is to catch a widened feature graph before paying for the full application build.

Framework-only iOS compilation is insufficient for a claim that an iOS sample is executable.

---

# 27. Latest validated evidence from ARCHITECTURE_VALIDATION_REPORT.md

The last provided validation report recorded the following working state before the newest SKILL refinements.

Important: treat these as **validated historical/current baseline evidence**, not proof that every rule newly added to the latest SKILL has already been implemented.

The report recorded:

```text
Architecture:
    READY FOR V1.0 at that validation point

architectureCheck:
    PASS
    411 project edges inspected
    invalid graph/source fixtures rejected

Android production:
    APK built
    installed
    launched on emulator
    initial UI/DI rendered

iOS production:
    KMP framework built
    native Pantrio.app built
    installed
    launched on iOS simulator
    initial UI/DI rendered

Kitchen sample:
    Android executable built/launched
    deterministic data rendered
    typed navigation exercised
    back verified

Cook sample:
    Android executable built/launched
    deterministic data rendered
    typed navigation exercised
    back verified

iOS Kitchen/Cook samples:
    framework built
    native executable built
    installed/launched
    deterministic UI visible
    interactive typed navigation/back was not exercised because UI automation runtime was unavailable
```

The validation report explicitly did **not** infer iOS navigation success from compilation alone.

---

# 28. Previously measured isolation evidence

At the last validation point:

```text
Production Android resolved project dependencies:
    50

Kitchen isolated sample:
    12

Cook isolated sample:
    12
```

The sample graphs contained no:

```text
app/shared
data project
unrelated presentation feature
production auth/network/storage/push graph
broad production fake graph
```

This was one of the strongest pieces of evidence supporting the architecture.

If the newest fixtures/isolationCheck changes were implemented after this report, re-measure the graph. Do not reuse these numbers automatically.

---

# 29. Previously measured Android build benchmarks

Environment recorded in the previous validation report:

```text
MacBook Pro Mac14,6
Apple M2 Max
12 CPU cores
32 GB RAM
macOS 26.5.1 arm64

Gradle 9.1.0
Kotlin plugin 2.4.0
AGP 9.0.1
Compose Multiplatform 1.11.1
Koin 4.2.1

Gradle build cache:
    enabled

Gradle configuration cache:
    enabled

dependency cache:
    warm/preserved
```

Three paired runs:

| Target | Scenario | Run 1 | Run 2 | Run 3 | Median |
|---|---:|---:|---:|---:|---:|
| Production `:app:android` | clean-output / warm-cache | 3.26 s | 3.27 s | 3.32 s | **3.27 s** |
| Production `:app:android` | no-change | 0.78 s | 0.71 s | 0.74 s | **0.74 s** |
| Kitchen sample | clean-output / warm-cache | 4.18 s | 1.27 s | 1.26 s | **1.27 s** |
| Kitchen sample | no-change | 2.02 s | 0.47 s | 0.47 s | **0.47 s** |
| Cook sample | clean-output / warm-cache | 2.49 s | 1.16 s | 1.16 s | **1.16 s** |
| Cook sample | no-change | 1.65 s | 0.47 s | 0.43 s | **0.47 s** |

The first sample pair paid configuration-cache recalculation after switching task graphs.

At that point:

```text
Kitchen clean median:
    ~61% lower than production

Cook clean median:
    ~65% lower than production
```

Use careful terminology.

Do **not** call these cold builds.

A more accurate label is:

```text
clean-output build with warm Gradle/dependency caches
```

or:

```text
developer-workstation clean build (warm caches)
```

---

# 30. Benchmark methodology still needed / newly required

The newest architecture specification improves the benchmark methodology.

Do not stop at:

```text
clean
no-change
```

Measure scenarios that directly test the isolation claim:

## A. Full app vs sample clean-output build

Question:

> How much of the tree does the feature developer avoid compiling?

## B. Full app vs sample no-change build

Question:

> What is the zero-change loop cost?

## C. Real feature source-change incremental build

Edit one small line in:

```text
presentation/<feature>
```

Then compare rebuilding:

```text
sample/<feature>
vs
app/android
```

This is the most important **inner-loop** benchmark.

## D. Data-only change

Edit one line in:

```text
data/<feature>
```

The isolated feature sample should ideally remain unaffected because:

```text
sample -> data
```

is forbidden.

This scenario directly proves whether that rule provides real build isolation.

## E. Shared design-system change

Edit one line in:

```text
core:designsystem
```

This is an expected worst case because many modules depend on it.

Do not hide the result.

## F. Configuration cost

Measure configuration separately where practical.

Module count can reduce compilation work while increasing configuration overhead.

---

# 31. Benchmark honesty rules

Always record:

```text
exact command
machine model
CPU/core count
RAM
OS
Java version
Gradle version
Kotlin version
AGP version
Compose version
daemon settings
parallelism/workers
build cache
configuration cache
dependency cache state
clean definition
incremental definition
number of repetitions
individual results
median
resolved project graph
```

A high-core workstation may flatter a heavily modularized architecture.

Do not generalize one M2 Max result to all laptops or CI agents.

Unfavorable benchmark results are still useful findings.

---

# 32. Gradle Isolated Projects: verify before asserting

The latest architecture documentation mentions Gradle Isolated Projects.

Treat any exact status/version wording as **temporally unstable**.

Do not rely on a hardcoded statement such as:

```text
"graduated from experimental to incubating in Gradle X"
```

without checking current official Gradle documentation.

Architectural intent:

```text
Isolated Projects is an optional optimization experiment,
not a hard architectural requirement.

Evaluate it against the project's current Gradle/AGP/KMP plugin matrix.

If incompatible:
    record the incompatibility
    revert the flag

Do not leave a partially working configuration enabled.
```

Measure configuration impact before and after if evaluating it.

---

# 33. Build-logic stability

Convention plugins are useful but the included build itself can become a global invalidation point.

Keep build logic:

```text
small
stable
narrowly scoped
```

Frequently changing rule data should preferably live in task inputs such as:

```text
allowlist files
forbidden-marker files
configuration files
```

rather than requiring edits to convention plugin source on every architecture-rule tweak.

A build-logic edit can invalidate configuration across the repository.

---

# 34. Shared design system as an isolation hotspot

`core:designsystem` is likely depended on by many feature presentation modules.

Therefore:

```text
core:designsystem change
    -> broad recompilation
```

is expected.

Do not pretend module isolation eliminates this.

Consider separating:

```text
stable theme/tokens
```

from:

```text
frequently edited shared components
```

only if measurements show the shared module is a meaningful bottleneck.

Avoid premature splitting.

---

# 35. Architecture should remain evidence-driven

Do not add abstractions merely to make the architecture look sophisticated.

Avoid by default:

```text
BaseUseCase
BaseRepository
generic repositories
global Event bus
global Effect bus
navigation service
giant Common module
giant Utils module
Manager dumping grounds
generic Result wrapper for symmetry
broad fake graph
new framework without a measured problem
extra platform target without a product requirement
unnecessary expect/actual
```

Add an abstraction only when an actual ownership, testability, platform, or isolation problem justifies it.

---

# 36. Historical decisions that are no longer current

Do not resurrect these older ideas unless new evidence justifies them.

## Old: navigator retained by ViewModel

Rejected.

Current:

```text
ViewModel -> typed Effect -> route graph -> navigation
```

## Old: untyped `Flow<ScreenEffect>`

Rejected.

Current:

```text
Flow<FeatureEffect>
```

## Old: `BasicViewModel`

Latest naming direction:

```text
MviViewModel
```

## Old: sample owns all feature fakes

Latest direction:

```text
fixtures/<feature>
```

when the fake has multiple consumers.

## Old: broad sample/shared fake graph

Rejected because it destroys isolation.

## Old: every Domain stream should be StateFlow

Rejected.

Current:

```text
Flow by default
StateFlow only for intentional current-state semantics
```

## Old: build isolation inferred from folder layout

Rejected.

Current:

```text
resolved dependency graph + isolationCheck
```

## Old: framework compilation == iOS verification

Rejected.

Native executable build is required.

---

# 37. Current open issues / next actions

Before considering the newest architecture revision fully validated, inspect the repo and resolve these items.

## 37.1 Fix fixtures consumer contradiction

Make all docs and machine checks agree:

```text
fixtures/<feature> consumers:
    presentation/<feature> TEST sources
    sample/<feature>

NOT:
    domain
    data
    app
    production presentation
    other features
```

## 37.2 Fix Effect teardown wording

Replace overly strict:

```text
Effects exclusively from viewModelScope
```

with ownership/lifetime-based wording.

Ensure async emissions cannot outlive the ViewModel.

Retain teardown tests.

## 37.3 Reverify Gradle Isolated Projects wording

Use current official Gradle documentation before making a version/status claim.

Prefer version-neutral architecture guidance.

## 37.4 Make DI statement framework-neutral

Do not claim all compile-time DI necessarily aggregates at app level.

Evaluate the actual DI/codegen build behavior.

The architecture rejects unwanted aggregation/coupling, not an entire category by name.

## 37.5 Confirm newest `fixtures` design is implemented

The latest SKILL includes `fixtures/<feature>`.

Do not assume it exists in application code.

Inspect:

```text
settings.gradle.kts
fixtures/
sample dependencies
presentation test dependencies
architectureCheck fixtures rules
```

If it is documentation-only, implement/validate it before treating it as source-of-truth architecture.

## 37.6 Confirm `isolationCheck` exists and works

The newest SKILL expects:

```bash
./gradlew isolationCheck
```

Verify it is actually implemented.

It must test resolved graphs, not just declared dependencies.

## 37.7 Re-run isolation graph measurements

If fixtures or allowlists changed the module graph, regenerate:

```text
production project dependency count
Kitchen sample graph
Cook sample graph
```

Do not copy the previous 50/12/12 values without remeasurement.

## 37.8 Add real source-change benchmark

Run the new benchmark matrix, especially:

```text
presentation feature edit -> app vs sample rebuild
data feature edit -> verify sample unaffected
core:designsystem edit -> known worst case
```

This is stronger evidence than clean/no-change timing alone.

---

# 38. Recommended work order for Codex

Follow this order.

```text
1. Read repository instructions and Git status
2. Read this handoff
3. Read latest SKILL and all references
4. Read latest validation report
5. Inspect actual module/dependency graph
6. Compare implementation against newest SKILL
7. Produce a short discrepancy list
8. Fix application code first where needed
9. Add/update tests and architecture/isolation checks
10. Run implementation-level checks
11. Update SKILL/reference docs only after implementation is proven
12. Run architectureCheck
13. Run isolationCheck
14. Build Android production executable
15. Build iOS production native executable
16. Build at least two Android feature samples
17. Build at least two iOS native feature sample executables
18. Run available runtime checks
19. Re-measure resolved sample graphs
20. Run source-change and build-isolation benchmarks last
21. Update ARCHITECTURE_VALIDATION_REPORT.md with real evidence
22. Return a concise summary
```

Do not benchmark an intermediate architecture.

Do not update documentation first and then force implementation to match it.

---

# 39. Required validation gates

At minimum discover and run repository-equivalent commands for:

```bash
./gradlew architectureCheck
./gradlew isolationCheck
./gradlew check
./gradlew :app:android:assembleDebug
./gradlew :app:shared:compileKotlinIosSimulatorArm64
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64
```

For at least two feature samples:

```bash
./gradlew :sample:<feature>:androidApp:assembleDebug
./gradlew :sample:<feature>:shared:linkDebugFrameworkIosSimulatorArm64
```

Also build actual iOS native executable sample schemes with Xcode.

Use actual discovered task/scheme names.

---

# 40. Runtime verification rules

When emulator/simulator access exists, verify:

```text
install
launch
initial UI
deterministic fixture data
DI startup
state update
typed Effect
navigation
back behavior
resources
changed flow
```

Never infer:

```text
runtime navigation passed
```

from:

```text
framework compiled
```

or even:

```text
native executable built
```

If a runtime interaction cannot be tested, state it explicitly.

---

# 41. Validation report expectations

The root report should remain:

```text
ARCHITECTURE_VALIDATION_REPORT.md
```

Update it with real results after the newest architecture changes.

It should include:

```text
Executive Summary
Application Changes
Domain Flow/StateFlow Decisions
Effect Transport Policy
Reducer Verification
Composition Ownership
Architecture Enforcement
Isolation Enforcement
MVI Tests
Feature Isolation Evidence
SKILL/Reference Updates
Android Production Build
iOS Production Build
Android Sample Builds
iOS Sample Builds
Benchmark Results
Commands Executed
Failures/Fixes
Remaining Risks
Final Architecture Status
```

Do not fabricate missing evidence.

---

# 42. Final v1.0 readiness rule

Do not claim the newest architecture revision is fully validated unless:

```text
architectureCheck passes
isolationCheck passes
invalid architecture fixtures fail correctly
sample/data isolation is proven from resolved graphs
fixture inbound/outbound rules are enforced
required tests pass
Android production executable builds
iOS production native executable builds
at least two Android samples build
at least two iOS native sample executables build
SKILL/reference docs match actual implementation
critical known contradictions are resolved
```

Runtime interactions unavailable because of environment/tool limits may remain explicitly disclosed rather than falsely marked as passed.

---

# 43. Medium / GitHub positioning

The strongest public positioning is not:

```text
"The best KMP architecture"
```

and not:

```text
"A completely new architecture"
```

Use something closer to:

> **A feature-isolated Kotlin Multiplatform architecture focused on independently buildable and runnable features.**

or:

> **Feature-Isolated KMP Architecture: Clean Architecture + Event-Reducer MVI + Independently Runnable Features**

The differentiator is the combination:

```text
KMP
+
feature-first Clean Architecture
+
Event-owned reducer MVI
+
typed one-shot Effects
+
runtime composition
+
fixtures per feature
+
independent Android/iOS samples
+
machine-enforced architecture rules
+
resolved-graph isolation checks
+
measured build isolation
```

The public story should be:

> Clean architecture is not the end goal. It is used to create measurable build isolation and a smaller feature development loop.

---

# 44. Medium benchmark story

When publishing results:

Do not write only:

```text
Full app: 3.27s
Sample: 1.27s
```

Always include environment/cache conditions.

Prefer:

```text
Clean-output / warm-cache median
Production Android: 3.27 s
Kitchen sample:     1.27 s
Cook sample:        1.16 s
```

Then show:

```text
Resolved project graph
Production: 50
Kitchen:    12
Cook:       12
```

But rerun these after the latest fixture/isolation changes before publishing.

The most important new chart/table should be:

```text
feature source edit
    full app rebuild
vs
    sample rebuild
```

because that measures the real inner development loop.

---

# 45. What Codex should do in its first response

After reading this file, the SKILL/references, validation report, and repository, Codex should **not edit files immediately**.

First return:

```text
1. Current architecture as implemented
2. Current architecture as specified by latest SKILL
3. Differences between implementation and SKILL
4. Which rules are already machine-enforced
5. Which rules are only documented
6. Whether fixtures/<feature> is already implemented
7. Whether isolationCheck is already implemented
8. Current sample dependency graphs
9. Open issues from this handoff that still apply
10. Recommended implementation order
```

Only then proceed with changes.

---

# 46. Core philosophy

Keep this principle throughout all future changes:

> **Do not optimize for the prettiest architecture diagram. Optimize for explicit ownership, independent feature development, measurable build isolation, and rules that can be verified by the build.**

If a new abstraction, module, framework, or dependency does not improve those properties, it probably does not belong in this architecture.
