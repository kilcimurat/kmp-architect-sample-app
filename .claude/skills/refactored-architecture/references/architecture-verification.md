# Architecture Verification, CI, and Benchmarks

## Contents

1. Enforcement strategy
2. Gradle dependency rules
3. Source and behavior rules
4. Feature-isolation proof
5. CI gates
6. Build benchmarks

## 1. Enforcement strategy

Use the lightest combination that checks actual rules:

- Gradle model inspection for declared project/external dependency directions;
- focused source checks where dependency metadata cannot detect controller/platform imports;
- behavioral tests for reducer purity, atomic state, effects, and DI graphs;
- resolved dependency reports for sample isolation.

Expose two root commands: `./gradlew architectureCheck` for declared edges and source rules, and
`./gradlew isolationCheck` for resolved sample graphs. Implement them in existing build logic
instead of adding heavyweight tooling unless the repository already uses a suitable architecture-test
library.

Keep them separate because they fail for different reasons and at different speeds:
`architectureCheck` reads declared metadata and source trees, while `isolationCheck` resolves real
classpaths. A developer who widens a sample by one project should see an isolation failure naming
that project, not a generic architecture error.

Do not implement architecture verification only as filename grep. Do not resolve every configuration
during normal checks; inspect declared edges and use selected resolvable classpaths for transitive
evidence.

## 2. Gradle dependency rules

At minimum reject:

```text
domain       → data, presentation, app, sample, fixtures
presentation → data, app, sample
data         → presentation, app, sample
core         → domain, data, presentation, app, sample, fixtures
fixtures     → data, presentation, app, another feature
sample       → app, data, another feature
<feature>    → any other feature, in every layer
```

`sample → data` and feature-to-feature edges deserve their own named failures. They are the two
rules that keep the development loop small, and they are the two most likely to be "temporarily"
violated to make something compile.

For domain, also reject external implementation/UI dependencies such as Compose, Android framework
libraries, Koin, persistence, network clients, and vendor SDKs. Adapt coordinate markers to the
project’s actual stack.

Also inspect dependency *configuration*, not only direction. Reject `api` declarations outside a
recorded allowlist: an unnecessary re-export silently restores the recompilation cascade the module
split was meant to remove, while every declared-edge rule still passes.

Capture project dependency edges from all declared configurations after project configuration, then
pass immutable strings into a cache-compatible verification task. Avoid reading `Project` at task
execution time. Restrict source inputs to real source trees so generated build outputs do not create
implicit task dependencies.

Keep classification functions separate and unit test examples of forbidden and allowed directions.

## 3. Source and behavior rules

Source checks may reject:

- Android/UIKit/Compose/Koin/data/presentation imports from domain;
- data implementation and native navigation-controller imports from presentation;
- feature imports from core;
- `MutableStateFlow<...Effect>` transient-effect storage;
- obvious coroutine/emission/navigation, repository/gateway/source, service-locator, implicit
  clock/randomness, and old-state collection-mutation markers inside actual `override fun reduce`
  bodies.

Discover reducer implementations from Kotlin structure/signatures, not filename suffixes. Keep the
lightweight extractor unit tested with block-bodied and expression-bodied reducers, side effects in
non-reducer methods, and filenames that do not contain `Event` or `Reducer`.

Treat reducer source scanning as a guardrail, not a proof. Add tests for:

- deterministic reduction and previous-state immutability;
- atomic concurrent state updates;
- ViewModel repository/use-case result → Event → expected State;
- Action → typed Effect;
- no effect replay after consumption;
- explicit rejection behavior after Effect transport closes;
- teardown ordering: cancelling `viewModelScope` before closing the transport, so normal clearing
  cannot trigger the fail-fast rejection;
- feature sample DI graph startup and populated deterministic state from `fixtures/<feature>`.

Prove rejection rules with fixtures/tests as well as a passing production tree. At minimum cover
domain→data, domain→presentation, presentation→data, core→feature, sample→app, **sample→data**,
**feature→feature**, **fixtures→data**, **unjustified `api`**, forbidden Domain
Koin/Compose/Android/UIKit imports, native controller ownership in Presentation, and replaying
`MutableStateFlow<Effect?>` storage.

## 4. Feature-isolation proof

Isolation is the architecture's central claim, so it is a gate, not an appendix. Give every sample a
declared allowlist stored beside it, resolve its real classpath, and fail the build on any project
outside the list:

```text
# sample/<feature>/isolation-allowlist.txt
:presentation:<feature>
:domain:<feature>
:fixtures:<feature>
:core:mvi
:core:designsystem
:core:navigation
```

```text
isolationCheck FAILED
  :sample:bookmarks:androidApp (debugRuntimeClasspath)
    unexpected project on the resolved graph: :data:bookmarks
    allowlist: sample/bookmarks/isolation-allowlist.txt
```

Widening a sample then becomes a reviewable diff to that file rather than an invisible regression.
Fail on unexpected entries; also report allowlist lines that no longer resolve, so the list cannot
rot into permanent over-permission.

Choose a concrete resolvable compile/runtime classpath rather than assuming isolation from folder
shape. Examples vary by plugin version:

```bash
./gradlew :sample:<feature>:shared:dependencies \
  --configuration iosSimulatorArm64CompileKlibraries

./gradlew :sample:<feature>:androidApp:dependencies \
  --configuration debugRuntimeClasspath
```

Inspect all transitive `project :...` nodes. Prove for every sampled feature that the graph excludes
the app root, `data/*`, unrelated features, broad fake graphs, and production side-effect
implementations.

Complement the project-level allowlist with an external-dependency assertion for at least one
sample: confirm that no network, persistence, or serialization artifact reaches the resolved graph.
Project-node counting alone will not notice a transitively re-exported client library.

Gradle may configure every project while producing a dependency report. A “Configure project” line
is not a dependency edge; distinguish configuration from the resolved graph.

## 5. CI gates

Prefer separate jobs or clearly separated steps:

```text
architecture-check
isolation-check
common/domain/presentation tests
android-production-build
ios-production-executable
android-samples (matrix)
ios-samples (matrix of native executable schemes)
```

Run `isolation-check` early and independently of the production build. Its whole value is telling a
contributor that a sample grew before anyone waits on a full application build.

Android sample jobs assemble every executable. Add emulator install/launch only when CI provides a
stable emulator. iOS sample jobs must build native Xcode targets; framework linking alone is
insufficient. Use simulator destinations and disable signing for ordinary pull requests.

Keep CI commands identical to local commands when possible. Generate Xcode projects from authoritative
specs before building when the repository uses a generator.

## 6. Build benchmarks

Build isolation is this architecture's explicit claim, so benchmarking is mandatory. Record:

- exact command and target;
- machine CPU/RAM/OS;
- Gradle, Java, Kotlin/AGP versions;
- daemon, configuration-cache, and build-cache settings;
- whether downloaded dependency caches are warm;
- clean versus incremental definition;
- project modules in each resolved graph.

Comparable example:

```bash
/usr/bin/time -p ./gradlew clean :app:android:assembleDebug \
  --no-daemon --no-build-cache --no-configuration-cache

/usr/bin/time -p ./gradlew :app:android:assembleDebug \
  --no-daemon --no-build-cache --no-configuration-cache
```

Measure the scenarios that correspond to the claim, not just total build time:

| Scenario | Question it answers |
|---|---|
| Full app vs one sample, clean | How much of the tree does a feature developer avoid compiling? |
| Full app vs one sample, no-change | What does the daily loop cost when nothing changed? |
| Edit one line in `presentation/<feature>`, rebuild sample vs app | The actual inner-loop delta |
| Edit one line in `data/<feature>` | Should not affect the sample at all — verifies the `sample → data` rule pays off |
| Edit one line in `core:designsystem` | The known worst case; report it rather than omitting it |
| Configuration time, cache on/off, Isolated Projects on/off | The cost side of module count |

Disclose the machine's core count and Gradle worker/parallelism settings prominently. A high-core
machine hides serialization costs and flatters parallel module builds; the same architecture on a
laptop can produce a materially different ratio, and a reader on a laptop is the likelier audience.

Repeat for at least two isolated sample apps. Define clean as removal of target build outputs through
the build's normal `clean` task, not deletion of downloaded dependencies. Define incremental/no-change
as an immediate identical invocation after a successful build. Run several repetitions, retain every
result, and report the median; label a single run directional only.

Report unfavorable or modest results honestly. Isolation can improve cognitive/dependency boundaries
even when configuration and shared Compose work limit incremental speedups. A result showing that
isolation cut compilation but added configuration overhead is a finding worth publishing, not a
problem to hide; suppressing it would make every other number in the report untrustworthy.
