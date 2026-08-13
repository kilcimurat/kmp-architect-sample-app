# iOS hosts

## Verified on macOS

The Kotlin frameworks and the real Swift/Xcode hosts were built on Apple Silicon with Xcode 26.2.
The production target and all three executable sample targets pass a generic iOS Simulator build.

| Thing | Verification |
|---|---|
| Kotlin iOS source and framework link | all four `linkDebugFrameworkIosSimulatorArm64` tasks pass |
| Xcode project generation | both XcodeGen specs generate successfully |
| Production Swift host | `KmpArchitectSampleApp` builds |
| Sample Swift hosts | `FeedSample`, `ArticleSample`, `BookmarksSample` build |
| iOS runtime | production and all three samples exercised on iPhone 17 Pro, iOS 26.2 |

The Xcode specs deliberately exclude simulator x86_64 because the KMP graph configures
`iosSimulatorArm64`. They also link system SQLite, required by the SQLiter driver. Sample Swift
module names end in `SampleHost`, keeping them distinct from imported Kotlin framework modules.
Every target's generated Info.plist contains `CADisableMinimumFrameDurationOnPhone = true`; Compose
requires this for high-refresh-rate iPhones and aborts at first render when it is missing.

## Layout and framework names

```text
iosApp/                     production host
├── project.yml             XcodeGen source of truth
├── Configuration/Config.xcconfig
└── Sources/KmpArchitectApp.swift

iosSamples/                 one project, three executable targets
├── project.yml
└── Sources/
    ├── Shared/SampleComposeRoot.swift
    ├── Feed/FeedSampleApp.swift
    ├── Article/ArticleSampleApp.swift
    └── Bookmarks/BookmarksSampleApp.swift
```

| Gradle project | Framework | Swift import |
|---|---|---|
| `:app:shared` | `AppShared` | `import AppShared` |
| `:sample:feed:shared` | `FeedSample` | `import FeedSample` |
| `:sample:article:shared` | `ArticleSample` | `import ArticleSample` |
| `:sample:bookmarks:shared` | `BookmarksSample` | `import BookmarksSample` |

## Native sharing boundary

The shared graph receives an `IosShareBridge` from the host. Swift supplies a live
`UIViewController`; shared code adapts the completion callback to the suspending domain `Sharer`
port. ViewModels and composables never locate or own a native controller.

## Generate, build and run

```bash
brew install xcodegen
./scripts/generate-xcode-projects.sh

xcodebuild -project iosApp/KmpArchitectSampleApp.xcodeproj \
  -scheme KmpArchitectSampleApp \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build

xcodebuild -project iosSamples/KmpArchitectSamples.xcodeproj \
  -scheme FeedSample \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Repeat the sample command for `ArticleSample` and `BookmarksSample`. Set `TEAM_ID` in
`iosApp/Configuration/Config.xcconfig` for device builds; simulator builds need no signing.

Recorded runtime acceptance on iPhone 17 Pro: production starts empty and Refresh loads four Demo
Backend articles; Feed and Bookmarks fixtures render, navigate to the
sample boundary and return; Article bookmark state changes and its fake Share emits `Shared.` without
a system sheet; production article navigation, bookmark persistence, native Share sheet and Saved
tab all work. After launch, verify process survival rather than relying only on `simctl launch` exit
status.
