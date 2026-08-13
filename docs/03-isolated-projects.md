# Gradle Isolated Projects: Evaluated and Deferred

## Decision

Isolated Projects is **not enabled**, and the migration that would make it possible is **not
scheduled**. The reason is measured, not assumed: the entire configuration phase this feature
parallelises is worth about one second here, and that second is skipped outright on a configuration
cache hit — which is the normal state of the edit-compile loop.

The migration is nonetheless *feasible*, and the only blocker is this repository's own root plugin.
That was verified rather than inferred, so the design below is kept for the day the trade changes.

The architecture SKILL already frames this correctly: Isolated Projects is "an optional, measured
optimisation — never an architectural requirement". This document is that measurement.

## Evidence

Host: AMD Threadripper PRO 5995WX, 128 cores, 251 GB RAM, Linux 6.8.0-124-generic.
Gradle 9.7.0, AGP 9.0.1, Kotlin 2.4.10, Compose Multiplatform 1.11.1, Koin 4.2.2.
Configuration cache and build cache enabled, dependency cache warm. Three runs per row, median
reported.

### How much configuration is there to parallelise?

| Command | Median |
|---|---:|
| `help` (configuration cache hit — configuration skipped) | 0.64 s |
| `help --no-configuration-cache` (all 37 projects configured) | 1.57 s |
| `:app:android:assembleDebug --dry-run --no-configuration-cache` | 1.71 s |
| `:sample:feed:androidApp:assembleDebug --dry-run --no-configuration-cache` | 1.49 s |
| `:sample:feed:androidApp:assembleDebug --dry-run` (cache hit) | 0.67 s |

Configuration costs roughly 0.8–1.0 s above the cache-hit floor, and only on a cache miss. Editing
Kotlin source does not invalidate the configuration cache, so the daily loop pays none of it and
Isolated Projects has nothing to win there.

This is the most favourable machine the feature could be measured on. A smaller laptop would spend
more absolute time in configuration and would see a proportionally larger benefit — but still only
on cache misses, and the migration cost would be identical.

### Is anything forcing the migration?

No. `./gradlew architectureCheck isolationCheck buildLogicTest --warning-mode all` reports no Gradle
10 deprecation for `allprojects`, `projectsEvaluated` or cross-project `configurations` access.
These APIs are incompatible with Isolated Projects but are not on a deprecation schedule.

The one Gradle 10 deprecation the build does report — "Using a Project object as a dependency
notation" — comes from AGP internals (`VariantDependenciesBuilder.build` via
`VariantManager.createTestComponents`), not from repository build scripts. It clears with an AGP
upgrade.

### Is the root plugin really the only blocker?

Yes, and this is the finding that makes the design below worth keeping.

With `id("kmpa.architecture")` temporarily commented out of the root build file:

```bash
./gradlew :sample:feed:androidApp:assembleDebug -Dorg.gradle.unsafe.isolated-projects=true
# BUILD SUCCESSFUL, 129 tasks, configuration cache entry stored, zero violations
```

AGP 9.0.1, KGP 2.4.10, Compose Multiplatform and SQLDelight are therefore Isolated Projects clean in
this repository. Restoring the plugin reproduces the documented failure, which also confirms the
flag was genuinely active for both runs:

```text
Isolated Projects is an incubating feature.
1 problem was found storing the configuration cache.
- Plugin 'kmpa.architecture': Project ':' cannot access 'Project.configurations'
  functionality on subprojects via 'allprojects'
```

## Revisit when

Any one of these changes the trade; none of them holds today:

- the project grows enough that cold configuration becomes a noticeable share of a real build;
- Gradle deprecates `allprojects` / `projectsEvaluated` / cross-project model access, turning the
  refactor into maintenance rather than optimisation;
- AGP or KGP make Isolated Projects the default, or a plugin the project depends on requires it;
- the root plugin needs reworking for an unrelated reason, at which point the design below is the
  shape to rework it into.

Re-measure the table above before acting on any of them. The numbers, not the feature's reputation,
decide.

## Known defect worth fixing independently

`ArchitectureConventionPlugin` records source roots as absolute paths:

```kotlin
sourceRoots += "${project.path}|${srcDir.absolutePath}"
```

That makes the `architectureCheck` cache entry machine-specific, so the task cannot share build
cache results between machines. Publishing repository-relative paths fixes it and is worth doing on
its own merits — it needs none of the migration below.

---

# Retained design

Everything below describes how the migration would be done. It is retained unexecuted.

## Goal

Make these commands work under Isolated Projects without weakening any architecture or
sample-isolation rule:

```bash
./gradlew architectureCheck isolationCheck \
  -Dorg.gradle.unsafe.isolated-projects=true
```

Preserve the current reports and public task names. Do not replace Gradle model inspection with
unreliable build-file text parsing.

## Blocker

`kmpa.architecture` is applied only to the root project. After all projects are evaluated it reads
mutable state belonging to every subproject:

```text
root plugin
├── allprojects
│   ├── project.configurations
│   ├── configuration.dependencies
│   └── project.layout
└── sample configuration
    └── debugRuntimeClasspath / iosSimulatorArm64CompileKlibraries resolutionResult
```

One project cannot inspect another project's mutable Gradle model. Fixing only the first
`allprojects` loop is insufficient: sample discovery, sample task registration and root-owned graph
resolution must all move out of the root project.

## Target design

Distributed producers with a file/artifact-based root aggregator:

```text
each project
└── kmpa.architecture-contributor
    └── architecture metadata artifact
        ├── project path
        ├── declared project edges
        ├── declared external edges
        └── production source roots

each sample androidApp and each sample shared module
└── kmpa.sample-isolation-contributor
    └── resolved isolation artifact
        ├── sample path
        ├── platform and configuration name
        ├── resolved project nodes
        └── resolved external modules

root project
└── kmpa.architecture
    ├── consumes contributor artifacts
    ├── architectureCheck
    └── isolationCheck
```

Every contributor reads only its own project. The root consumes immutable files through Gradle
configurations and never touches a subproject's `configurations`, `dependencies`, `tasks`,
`extensions` or `layout`.

## Non-goals

- Do not change architecture rules or allowlists.
- Do not merge `architectureCheck` and `isolationCheck`.
- Do not enable Isolated Projects before all gates pass.
- Do not parse `build.gradle.kts` text to infer dependency edges.
- Do not make samples depend on production modules to simplify aggregation.
- Do not change developer-facing task names or report locations.

## Phase 0 — freeze current behavior

```bash
./gradlew buildLogicTest architectureCheck isolationCheck --rerun-tasks
cp build/reports/architecture/architecture-check.txt /tmp/architecture-check.before.txt
cp build/reports/architecture/isolation-feed-android.txt /tmp/isolation-feed-android.before.txt
cp build/reports/architecture/isolation-feed-ios.txt /tmp/isolation-feed-ios.before.txt
# ...and the article and bookmarks reports for both platforms
```

Retain rule tests proving that forbidden project edges, forbidden external dependencies, unjustified
`api` edges, fixtures inbound/outbound violations, `sample -> data` on **both platforms**, production
network/database reach and stale allowlists all fail, and that all six current sample graphs pass.

The dominant risk in this migration is not slowness but a vacuum pass: a contributor that misses
configurations added later by AGP or KGP reports zero edges, and the gate goes green while checking
nothing. Golden report comparison is what catches that.

Acceptance: baseline reports and rule-test results saved before structural changes.

## Phase 1 — serializable metadata models

Build-logic models containing no Gradle `Project`, `Configuration`, dependency or resolution objects.
A deterministic line-oriented format avoids adding a JSON library to build-logic:

```text
PROJECT|:presentation:feed|:domain:feed|commonMainImplementation
EXTERNAL|:presentation:feed|org.jetbrains.kotlinx:kotlinx-coroutines-core|commonMainImplementation
SOURCE_ROOT|:presentation:feed|src
```

```text
SAMPLE|:sample:feed:androidApp|android|debugRuntimeClasspath
PROJECT_NODE|:core:model
MODULE_NODE|org.jetbrains.kotlinx:kotlinx-coroutines-core
```

Sort and deduplicate records, use repository-relative source paths, reject malformed records with a
useful error, unit-test round trips and stable ordering, and keep rule evaluation independent from
serialization.

Acceptance: round-trip tests pass and output is byte-for-byte deterministic.

## Phase 2 — per-project architecture contributor

A project-level plugin (`kmpa.architecture-contributor`) that inspects only the current project's
configurations, records declared `ProjectDependency` and `ExternalModuleDependency` edges, excludes
the synthetic SwiftPM lock metadata configuration, records the project's `src` root when present,
registers a cacheable metadata task, and publishes it through a consumable, non-resolvable
`architectureMetadataElements` configuration.

Constraints: no `rootProject.allprojects`, `subprojects` or `project(":...").configurations`; no
`Project`/`Configuration`/dependency instances as task inputs; capture immutable strings and file
paths before execution; observe configurations added by KMP/Android plugins regardless of plugin
order; stay configuration-cache compatible and cacheable.

Apply it through the existing convention plugins so each project applies it to itself. If universal
application cannot be guaranteed cleanly, use a settings plugin with `gradle.lifecycle.beforeProject`
— never a root `allprojects` callback.

Acceptance: every non-root project publishes one artifact containing only its own edges and source
root; no project reads another's model; contributor tests pass under Isolated Projects.

## Phase 3 — aggregate metadata at the root

Refactor `kmpa.architecture` to create a resolvable `architectureMetadata` configuration consuming
contributor artifacts through a dedicated category/usage attribute, so production variants cannot be
selected accidentally.

Project membership must come from immutable settings-time structure, not from reading configured
subproject models. In order of preference: a settings plugin creating root metadata dependencies
from `ProjectDescriptor` paths; failing that, a generated module manifest — which then needs a test
asserting it matches the `settings.gradle.kts` includes, or a new module silently escapes the check.

`ArchitectureCheckTask` accepts metadata files instead of root-collected `ListProperty` values, and
invokes the existing `DependencyRules` and `SourceRules` unchanged.

Acceptance: same edge/source counts and zero-violation result as baseline; invalid-edge fixtures fail
with the same rule names; no `allprojects`, `subprojects` or foreign `Project.configurations` in the
root plugin; configuration cache stores and reuses. Also assert that `architectureCheck --dry-run`
pulls in no compile tasks — aggregation through artifacts must not drag the build graph along.

## Phase 4 — move isolation resolution into each sample

A contributor plugin applied to `:sample:<feature>:androidApp` and `:sample:<feature>:shared`, each
resolving its own configuration (`debugRuntimeClasspath` and `iosSimulatorArm64CompileKlibraries`),
walking `ResolutionResult` locally, producing deterministic artifacts, binding the sample's
`isolation-allowlist.txt` as an input, running the existing checks locally and publishing results
through `sampleIsolationMetadataElements`.

Keep the root aggregate command `isolationCheck`. The root consumes result artifacts; it does not
access sample configurations or register tasks by mutating sample projects. Discover samples from
immutable settings project paths, not from filesystem folders.

Acceptance: all six reports match baseline nodes; a `sample -> data` fixture fails locally and
through the root aggregate on both platforms; forbidden transitive externals and stale allowlist
entries still fail; requesting one sample's check does not resolve another's graph. The failure
message must still name the offending project and platform as clearly as the current one — that
specificity is the gate's practical value, not a cosmetic detail.

## Phase 5 — remove central cross-project access

Delete from the root plugin: `gradle.projectsEvaluated` used for cross-project collection, both
`target.allprojects` traversals, foreign `project.configurations` reads, root-owned resolution of
sample configurations, and root registration that mutates or captures sample project state.

```bash
rg -n "allprojects|subprojects|projectsEvaluated|\.configurations" build-logic/src/main/kotlin
```

Every remaining match must operate on the plugin's own project or carry a documented
Isolated-Projects-safe reason.

Acceptance: the old central implementation is removed, not left as an alternate code path.

## Phase 6 — validation matrix

```bash
./gradlew buildLogicTest architectureCheck isolationCheck --rerun-tasks
./gradlew check
```

Compare against the Phase 0 baseline; counts and graph contents must match unless a reviewed,
explained correction is found. Then run diagnostics, and strict mode only once diagnostics is clean:

```bash
./gradlew help -Dorg.gradle.unsafe.isolated-projects=true \
  -Dorg.gradle.unsafe.isolated-projects.diagnostics=true
./gradlew architectureCheck isolationCheck -Dorg.gradle.unsafe.isolated-projects=true
./gradlew check -Dorg.gradle.unsafe.isolated-projects=true
./gradlew :sample:feed:androidApp:assembleDebug -Dorg.gradle.unsafe.isolated-projects=true
./gradlew :sample:bookmarks:androidApp:assembleDebug -Dorg.gradle.unsafe.isolated-projects=true
```

Verify the property name against the Gradle documentation for the version in use before trusting the
spelling recorded here. Repeat key commands twice to prove configuration cache reuse, not only
storage.

Acceptance: zero constraint violations; ordinary and isolated reports identical; `check`, production
and two samples pass; configuration cache reused; no warning mode suppressing violations.

## Phase 7 — benchmark before enabling

Measure configuration and representative developer loops with and without the flag: cold
configuration, production `assembleDebug`, two sample `assembleDebug`s, no-change repeat,
presentation edit, data edit, design-system edit. Record individual runs and medians on one machine.

Enable by default only if stable and not a regression for the daily sample loop. A neutral or
negative result is valid — compatibility alone still removes a future migration blocker.

Acceptance: `docs/02-benchmarks.md` and the raw TSV distinguish compatibility from measured
performance.

## Phase 8 — documentation

Update `ARCHITECTURE_VALIDATION_REPORT.md`, `docs/00-toolchain.md` and `docs/02-benchmarks.md` with
exact commands and results, and replace the decision above with the real compatibility status.
Update the architecture SKILL only if the implementation introduces a reusable rule not already
documented. Do not claim a speed improvement unless the measurements support it.

## Suggested file map

```text
build-logic/src/main/kotlin/.../
├── ArchitectureConventionPlugin.kt          # root aggregation only
├── ArchitectureContributorPlugin.kt         # local declared metadata
├── ArchitectureMetadataTask.kt              # writes local immutable artifact
├── SampleIsolationContributorPlugin.kt      # local resolved sample graph, per platform
├── SampleIsolationMetadataTask.kt           # writes/checks local graph
├── ArchitectureCheckTask.kt                 # consumes metadata artifacts
├── IsolationAggregateTask.kt                # validates/aggregates sample results
└── rules/
    ├── ArchitectureMetadata.kt              # serialization/parser models
    ├── DependencyRules.kt                   # unchanged semantic rules
    ├── SourceRules.kt                       # unchanged semantic rules
    └── IsolationRules.kt                    # unchanged semantic rules
```

Extend existing task/rule classes where that keeps responsibilities clear; do not create classes
solely to match this illustrative tree.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| KMP/AGP configurations appear after contributor setup | plugin callbacks and `configureEach`; test real modules |
| Metadata variant conflicts with production variants | dedicated attributes/category and artifact type |
| Root aggregation drags in the whole task graph | consume small metadata artifacts; assert with `--dry-run` |
| Generated configuration creates false SwiftPM edges | preserve and test the existing synthetic-config exclusion |
| Source paths make cache entries machine-specific | publish repository-relative paths |
| Sample graph silently differs from baseline | golden report comparison and negative fixtures |
| Isolated mode passes only with warnings suppressed | forbid `org.gradle.configuration-cache.problems=warn` in acceptance |
| Global enablement slows daily work | benchmark first; keep opt-in if benefit is not demonstrated |

## Rollback

Implement in small slices, keeping ordinary `architectureCheck` and `isolationCheck` green. Until the
new aggregator reaches parity, retain the old implementation behind a temporary internal switch used
only for report comparison, then remove that switch before final acceptance.

If a phase fails, revert that phase. Do not weaken rules, widen allowlists or enable Gradle warning
mode to make the build green.

## Definition of done

- root build logic never reads mutable subproject state;
- every project publishes local declared-dependency/source metadata;
- every sample resolves its own graph, per platform;
- root task names and report paths remain stable;
- architecture and isolation results remain equivalent to baseline;
- negative fixtures still prove rejection behavior;
- `help`, `architectureCheck`, `isolationCheck`, `check`, production and two samples pass in strict
  Isolated Projects mode;
- configuration cache reuse demonstrated;
- performance remeasured before global enablement;
- validation report and benchmark documentation reflect real evidence.
