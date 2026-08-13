# Build-isolation benchmarks

Reproduce with `./scripts/benchmark.sh`. Raw repetitions are written to
`build/benchmark-results.tsv`. The script restores edited sources from private backups, so it also
works safely in exported workspaces without Git metadata.

## Environment and method

| | |
|---|---|
| Host | macOS (Darwin), Apple Silicon, 12 logical cores, 32 GB RAM |
| Gradle projects | 30 |
| Repetitions | 3 cold, 5 warm; median reported |
| Cold | `clean`, no build cache, no configuration cache |
| Warm/edit | daemon and caches enabled |

Two independent sample graphs are measured: Feed and Bookmarks. Task count is the portable
dependency-scope signal; wall time is machine-specific and can be noisy.

## Results

| Scenario | Production | Feed sample | Bookmarks sample |
|---|---:|---:|---:|
| cold build | 9353 ms / 236 tasks | 3579 ms / 125 | 3478 ms / 125 |
| warm, no change | 615 ms / 20 | 569 ms / 9 | 498 ms / 9 |
| presentation edit | Feed: 1465 ms / 24 | 2009 ms / 13 | — |
| presentation edit | Bookmarks: 1307 ms / 24 | — | 1262 ms / 13 |
| data edit | Feed: 766 ms / 24 | **450 ms / 9** | — |
| data edit | Bookmarks: 740 ms / 24 | — | **622 ms / 9** |
| design-system edit | 1209 ms / 24 | 870 ms / 13 | 751 ms / 13 |

Both data edits leave their sample at the same 9-task no-change graph: sample composition roots do
not depend on production data modules. Cold sample builds execute 47% fewer tasks than production;
presentation/design-system edit graphs execute about 46% fewer.

Wall-clock results are intentionally not presented as a universal speedup. Feed's presentation
sample median was slower than production despite fewer tasks, illustrating scheduler/cache noise
on short warm builds. The defensible result is smaller and enforced build scope.

## Configuration and Isolated Projects

Cold configuration-cache population measured 1002 ms / 1 task. The Isolated Projects variant was
attempted five times and failed five times with the same constraint violation:

```text
Plugin 'kmpa.architecture': Project ':' cannot access 'Project.configurations'
functionality on subprojects via 'allprojects'
```

The option remains disabled. This follows Gradle's migration guidance: a build with a detected
cross-project state violation is not reliable and must fail. The known migration is to make each
project publish its own dependency-edge artifact and aggregate those artifacts without root-level
mutable project access. This is a build-performance optimization, not a functional architecture
requirement, and is recorded as the remaining limitation rather than hidden.

That limitation was later measured and closed as a decision rather than left pending: the whole
configuration phase is worth about one second here and is skipped entirely on a configuration cache
hit, and the root plugin was confirmed to be the only blocker. See
[03-isolated-projects.md](03-isolated-projects.md) for the numbers, the feasibility probe and the
conditions that would justify revisiting it.

