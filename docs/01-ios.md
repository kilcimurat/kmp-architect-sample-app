# iOS hosts

## What is verified, and what is not

This repository was built on Linux. That splits the iOS work cleanly in two, and the split is worth
stating precisely because the failure mode is a build that looks green and proves nothing.

| Thing | Verified here | Why |
|---|---|---|
| All iOS-targeted Kotlin (`commonMain` + `iosMain`) compiles | **Yes** | `compileKotlinIosSimulatorArm64` and `compileKotlinIosArm64` genuinely execute on Linux and produce klibs |
| Framework base names match the Swift `import`s | **Yes** | `./gradlew :<module>:printFrameworkName` |
| Framework linking | **No** | The Linux Kotlin/Native distribution has no Apple targets (`android_*`, `linux_*`, `mingw_x64` only) |
| Xcode project generation, Swift compilation, simulator run | **No** | No Xcode on this machine |

**The trap:** `linkDebugFrameworkIosSimulatorArm64` on Linux is *silently skipped* and the build
still reports `BUILD SUCCESSFUL`. A Linux CI job running it goes green without linking anything. iOS
gates must run on macOS runners; a green Linux build is never iOS verification.

So: the Swift sources and Xcode specs below are authored, reviewed and internally consistent, but
**not compiled**. Expect to fix something on the first Mac run.

## Layout

```
iosApp/                     production host
├── project.yml             XcodeGen spec  (source of truth)
├── Configuration/Config.xcconfig
└── Sources/KmpArchitectApp.swift

iosSamples/                 one project, three executable targets
├── project.yml
└── Sources/
    ├── Shared/SampleComposeRoot.swift    shared by all three
    ├── Feed/FeedSampleApp.swift
    ├── Article/ArticleSampleApp.swift
    └── Bookmarks/BookmarksSampleApp.swift
```

The `.xcodeproj` files are generated and git-ignored. A pbxproj is a merge-conflict machine full of
generated UUIDs; a spec is reviewable, and three near-identical sample targets stay identical
because they share a `targetTemplates` entry.

## Framework names

Derived from the Gradle path by the `kmpa.kmp.framework` convention plugin, because four projects
are named `shared` and `project.name` alone would collide:

| Gradle project | Framework | Swift |
|---|---|---|
| `:app:shared` | `AppShared` | `import AppShared` |
| `:sample:feed:shared` | `FeedSample` | `import FeedSample` |
| `:sample:article:shared` | `ArticleSample` | `import ArticleSample` |
| `:sample:bookmarks:shared` | `BookmarksSample` | `import BookmarksSample` |

Confirm at any time with `./gradlew :app:shared:printFrameworkName`.

## The one native capability

Sharing needs a live `UIViewController`, so the host provides it through a narrow callback
interface rather than the shared code reaching for a root view controller:

```kotlin
interface IosShareBridge {
    fun share(title: String, url: String, onResult: (Boolean) -> Unit)
}

fun AppViewController(shareBridge: IosShareBridge): UIViewController
```

`IosShareBridge` is deliberately **not** a `suspend` function: Kotlin/Native cannot have Swift
implement one. Swift gets a completion callback; `IosSharer` on the Kotlin side adapts it back into
the suspending `Sharer` port the domain layer uses. Nothing else about iOS leaks upward.

## Running it on a Mac

```bash
brew install xcodegen
./scripts/generate-xcode-projects.sh

# Production app
open iosApp/KmpArchitectSampleApp.xcodeproj          # scheme: KmpArchitectSampleApp

# Feature samples — one runnable app per feature
open iosSamples/KmpArchitectSamples.xcodeproj        # schemes: FeedSample, ArticleSample, BookmarksSample
```

Set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` for device builds; simulator builds need no
signing.

Command line, without opening Xcode:

```bash
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosSamples/KmpArchitectSamples.xcodeproj -scheme FeedSample \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Verification checklist for the first Mac run

Run in this order; each step isolates a different failure.

1. `./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64` — Kotlin links at all.
2. `./scripts/generate-xcode-projects.sh` — specs are valid.
3. Build `FeedSample` for a simulator — the Gradle build phase, framework search and Swift
   compilation all work. This is where a wrong framework name would surface.
4. Run `FeedSample`: fixture articles render, tapping one reaches the placeholder screen, back
   returns to the list.
5. Run `ArticleSample`: tapping Share must **not** open a system share sheet — the sample binds a
   recording fake, and a real sheet means the wrong `Sharer` was bound.
6. Run `BookmarksSample`: saved articles render, tapping one reaches the placeholder.
7. Build and run the production app: empty feed, Refresh syncs the demo backend, tapping an article
   opens the real article screen, Share **does** open the system sheet here, bookmarking moves the
   article into the Saved tab.

Step 5 versus step 7 is the point: the same screen, the same code, a different `Sharer` chosen by
the composition root.
