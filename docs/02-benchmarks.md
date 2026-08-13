# Build-isolation benchmarks

The architecture's central claim is that working on one feature does not mean building the whole
app. This is the measurement of that claim, including the parts where it looks unimpressive.

Reproduce with `./scripts/benchmark.sh`. Raw runs are in `build/benchmark-results.tsv`.

## Environment — read this before the numbers

| | |
|---|---|
| CPU / RAM | **128 cores**, 251 GB |
| OS | Linux x86_64 |
| Gradle | 9.7.0, daemon JVM Azul 21 |
| Kotlin / AGP / Compose MP | 2.4.10 / 9.0.1 / 1.11.1 |
| Gradle projects | 30 |
| Repetitions | 3 (cold), 5 (warm), median reported |

**A 128-core machine flatters this architecture.** Extra parallel work is nearly free here, and most
readers are on a laptop with 8–12 cores where the same extra work is not free. That is why every
scenario reports **tasks executed** alongside wall clock: task count is a property of the dependency
graph, so it transfers to your machine. Wall clock does not.

Two families:

- **cold** — `clean` + `--no-build-cache --no-configuration-cache`. How much work the graph really
  requires. Without disabling the build cache this measures cache-restore throughput; the first
  version of this script did exactly that and reported a "clean build" in 1.5 s.
- **warm** — daemon up, both caches on. What a developer actually feels.

Compared throughout: `:app:android:assembleDebug` (whole app) versus
`:sample:feed:androidApp:assembleDebug` (one feature).

## Results

| Scenario | App ms | App tasks | Sample ms | Sample tasks | Task reduction |
|---|---:|---:|---:|---:|---:|
| cold build | 5228 | 236 | 4235 | 125 | **−47%** |
| warm, no change | 748 | 20 | 722 | 9 | −55% |
| edit `presentation/feed` (non-ABI) | 1317 | 24 | 1298 | 13 | −46% |
| edit `data/feed` (non-ABI) | 1121 | 24 | **711** | **9** | **−63%** |
| edit `core:designsystem` (non-ABI) | 1082 | 24 | 1047 | 13 | −46% |
| edit `core:designsystem` (**ABI**) | 1472 | 30 | 967 | 14 | −53% |

Configuration, cold configuration cache: **1519 ms** for 30 projects, 1 task executed.

## What the numbers actually say

**1. The `sample → data` rule is the one that pays.**

Editing `data/feed` and rebuilding the feed sample costs **711 ms / 9 tasks** — identical to the
no-change baseline of 722 ms / 9 tasks. Not "less work": *no* work. The data module is not on the
sample's graph, so nothing to do. The same edit costs the app 1121 ms / 24 tasks.

This is the single clearest result in the set, and it is the rule the architecture spends the most
enforcement on. It is also the rule most likely to be broken casually, which is why `isolationCheck`
fails the build rather than warning.

**2. Task count halves. Wall clock barely moves.**

Editing presentation: 24 → 13 tasks, but 1317 ms → 1298 ms. A **46% reduction in work produced a
1.4% reduction in time.** On 128 cores the extra eleven tasks run alongside the others and cost
almost nothing.

Do not read this as "isolation is pointless". Read it as: *on this machine* the build was never the
bottleneck, so removing work from it changes little. On a laptop where 24 parallel tasks contend for
8 cores, a 46% cut is a different story — but this repository has not measured that, and it will not
claim it.

**3. Compile avoidance works, and it is why the "worst case" is milder than expected.**

A non-ABI edit to `core:designsystem` — the module everything depends on — costs the app the same 24
tasks as a leaf presentation edit. Kotlin's ABI snapshots mean a comment or a private body change
does not cascade.

Making it a real ABI change (adding a public function) raises the app to 30 tasks and the sample to
14. So the cascade is real but modest, and the sample still does less than half the app's work.

The first version of this benchmark only appended comments and would have reported the shared design
system as costless — a flattering result produced by measuring the wrong edit.

**4. Configuration is the cost side, and it does not shrink.**

1519 ms to configure 30 projects, before compiling anything. Isolation trades compilation for
configuration: more modules always means more configuration, and no dependency rule reduces it.
At this module count it is roughly equal to a warm incremental build — i.e. non-trivial.

**5. Isolated Projects is blocked — by this project's own architecture check.**

```
Plugin 'kmpa.architecture': Project ':' cannot access 'Project.configurations'
functionality on subprojects via 'allprojects'
```

Gradle's Isolated Projects is the direct countermeasure to finding 4, and it worked when this
repository had two modules. At 30 modules it fails, because `architectureCheck` collects declared
dependency edges by reading every subproject's configurations from the root — precisely what
Isolated Projects forbids.

The tool that enforces build isolation is what prevents enabling the build feature that would
improve it. The fix is known: have each project emit its own edges to a file and aggregate the files
at the root. It is not implemented here, and the flag stays off. Reported rather than hidden,
because a benchmark that omits its own blocker is advertising.

## Honest summary

Measured on this hardware, this architecture:

- **removes work reliably** — 46–63% fewer tasks in every scenario, and *zero* work for a
  data-layer change;
- **saves little wall-clock time here** — 1–19%, because 128 cores absorb the difference;
- **adds configuration cost** — ~1.5 s for 30 projects, which does not go away;
- **cannot currently use** the Gradle feature that would offset that cost, for a reason of its own
  making.

The strongest defensible claim is therefore about *scope*, not speed: a feature developer builds a
graph of 9–14 tasks instead of 20–30, runs one app instead of the whole product, and cannot
accidentally depend on the network or database stack because the build fails if they try. Whether
that converts into meaningful wall-clock savings depends on hardware this repository has not
measured.
