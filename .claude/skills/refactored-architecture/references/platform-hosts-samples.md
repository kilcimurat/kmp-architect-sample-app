# Platform Hosts and Feature Samples

## Contents

1. Host boundary
2. Production Android and iOS
3. Framework embedding
4. Preferred sample topology
5. Feature-local fixture composition
6. Android and iOS sample hosts
7. Resources and platform services
8. Runtime verification

## 1. Host boundary

Native hosts may own:

- executable lifecycle;
- application/bundle IDs, signing, manifests, plist/entitlements;
- SDK bootstrap requiring native configuration;
- Activity/SwiftUI/UIKit wrappers;
- platform DI implementation selection;
- embedding a shared static framework.

They must not duplicate shared screens, reducers, repositories, or business rules.

`app/shared` owns the shared `App()` surface, top-level typed navigation, cross-feature route
decisions, shared composition selection, and a stable exported iOS entry point. Native hosts own
executable lifecycle and platform runtime objects. Features must never depend on `app/shared`, and
Swift should consume the narrow exported factory rather than manually assembling internal Kotlin
graphs.

## 2. Production Android and iOS

Preferred Android structure:

```text
app/android/src/main/
├── AndroidManifest.xml
├── kotlin/<package>/ProductApplication.kt
├── kotlin/<package>/MainActivity.kt
└── res/                  # native icons/XML/provider metadata
```

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}
```

Initialize process-lifetime SDKs once. Assemble Activity-dependent or suspend-initialized adapters in
an explicit bootstrap before rendering dependent UI.

Preferred iOS structure:

```text
iosApp/
├── project.yml or .xcodeproj
├── Supporting/{Info.plist,*.entitlements,service files}
└── Sources/ProductApp.swift
```

Kotlin entry:

```kotlin
fun ProductAppViewController(): UIViewController {
    startProductKoinIfNeeded()
    return ComposeUIViewController { App() }
}
```

Swift entry:

```swift
import ProductShared
import SwiftUI

@main
struct ProductApp: App {
    var body: some Scene {
        WindowGroup { ComposeRoot().ignoresSafeArea(.all) }
    }
}
```

Keep usage descriptions, URL schemes, push/background modes, native service files, and required SDK
ordering in the native host.

Every Compose iOS executable host must put this entry in its authoritative Info.plist source. For
XcodeGen, define it under the target's `info.properties` so regeneration cannot discard it:

```yaml
info:
  path: Supporting/Info.plist
  properties:
    CADisableMinimumFrameDurationOnPhone: true
```

Compose performs a strict runtime plist sanity check. Omitting the key can let framework and Xcode
builds pass while every installed application aborts on first render. Do not disable the check with
`enforceStrictPlistSanityCheck = false`; preserve the high-refresh-rate behavior instead.

## 3. Framework embedding

Export a static framework for every supported Apple target. The Xcode build phase must:

1. select configuration and SDK/architecture correctly;
2. invoke the matching Gradle embed-and-sign/link task;
3. use repository-relative paths;
4. fail clearly when Java/Gradle prerequisites are unavailable;
5. avoid developer-specific absolute paths.

With XcodeGen, edit the YAML spec and regenerate the `.xcodeproj`.

Verify device/simulator framework compilation as applicable, but do not equate framework compilation
with an executable host build. Normal pull-request CI should use unsigned simulator builds rather than
requiring device signing.

## 4. Preferred sample topology

The sample is the primary development loop, not a demo built after the fact. A feature developer's
normal command is `:sample:<feature>:androidApp:installDebug`, and a feature is not finished until
its sample runs on both platforms. Everything below follows from that.

```text
sample/<feature>/
├── shared/                    # KMP library and static iOS framework
│   ├── commonMain             # sample graph/root, use-case/root wiring, fixture selection
│   ├── iosMain                # ComposeUIViewController factory
│   └── commonTest             # DI startup tests
├── androidApp/                # com.android.application
├── iosApp/                    # optional per-feature Xcode executable
└── isolation-allowlist.txt    # projects this sample's resolved graph may contain
```

Deterministic implementations live in `fixtures/<feature>`, not here — the sample selects them.
That keeps one fake shared with the feature's own tests instead of two drifting copies.

Alternatively:

```text
iosSamples/
├── one Xcode project
└── one executable scheme/target per feature framework
```

The centralized Xcode form is preferred when wrappers are identical. It preserves executable samples
without multiplying project files.

Avoid names such as `<feature>-IOS` for a module that contains shared KMP behavior and both Android/iOS
targets. When an existing repository cannot be moved safely, `<feature>-kmp`, `<feature>-android`, and
an Xcode `<feature>-ios` host are an acceptable fallback.

## 5. Feature-local fixture composition

A feature sample graph should resemble:

```text
sample/<feature>/shared
├── feature presentation module
├── reusable feature use-case factories
├── fixtures/<feature> (deterministic domain-port implementations)
└── only required core/domain dependencies
```

It must not transitively pull:

- the production app root;
- `data/<feature>` — the feature's own real repositories included;
- unrelated presentation/data features;
- production auth/network/storage/analytics/push adapters;
- broad fallback graphs that bind everything;
- persistent stores unless persistence is the sample’s explicit purpose.

Excluding the feature's own `data` module is deliberate and is where most of the build-time saving
comes from: no client library, no database driver, no serialization, no DTO mapping compiles during
feature development. When a sample genuinely needs the real implementation, that sample's purpose is
integration, and its allowlist must say so.

Example:

```kotlin
fun sampleItemModules(): List<Module> = listOf(
    module {
        single<ItemRepository> { FakeItemRepository(FixedItems.all) }  // from fixtures/<feature>
        factory { ObserveItems(get()) }
        factory { SaveItem(get(), get()) }
    },
    itemPresentationModule,
)
```

Use fixed/seeded data, time, and randomness. Make unavailable external operations return explicit safe
results rather than silently calling real services.

## 6. Android and iOS sample hosts

Android build:

```kotlin
plugins { alias(libs.plugins.androidApplication) }

android.defaultConfig {
    applicationId = "<application-id>.sample.<feature>"
}

dependencies {
    implementation(project(":sample:<feature>:shared"))
    implementation(libs.koin.android)
}
```

This module's `installDebug` task is the feature developer's daily command. Check what it actually
builds: if the task graph reaches `data/*`, `app/*`, or another feature, the isolation is nominal.

```kotlin
class SampleFeatureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SampleFeatureApplication)
            modules(sampleFeatureModules())
        }
    }
}
```

Use unique application IDs so samples coexist on an emulator.

The KMP sample exports:

```kotlin
fun SampleFeatureViewController(): UIViewController {
    startSampleKoinIfNeeded(sampleFeatureModules())
    return ComposeUIViewController { SampleFeatureRoot() }
}
```

The Xcode executable imports that feature framework and calls the factory. Use unique bundle IDs,
products, schemes, and framework names.

Sample route graphs must consume every feature Effect they collect. When an Effect normally opens a
feature intentionally outside the isolated graph, navigate to a small sample-local typed destination
that identifies the request and supports back navigation. Do not use an empty Effect handler and do
not pull the unrelated production feature into the sample merely to satisfy navigation.

## 7. Resources and platform services

Place shared UI copy/images/fonts/vector assets in Compose resources owned by shared UI modules.
Keep Android launcher/notification resources and iOS app/launch assets in native hosts.

Do not reference Android `R` IDs from `commonMain` except through focused platform APIs. Do not
duplicate shared production UI copy in Swift/Kotlin wrappers.

Use interfaces plus DI for permissions, camera, notifications, authentication, billing, sharing,
storage, and analytics when lifecycle/configuration/fakes matter. Samples supply safe implementations
by default.

## 8. Runtime verification

Run final platform and sample builds only after application changes pass tests and architecture
checks and the architecture documentation has been synchronized with those proven changes.

For each changed production/sample target, record build and runtime evidence:

| Target | Build | Install/launch | Initial UI/fakes | DI | Navigation/state | Platform integration |
|---|---:|---:|---:|---:|---:|---:|
| Android emulator/device | required | when available | required for samples | required | changed flows | when changed |
| iOS simulator | required native executable | when available | required for samples | required | changed flows | when changed |
| iOS device | framework/signed build when available | release-dependent | release-dependent | release-dependent | release-dependent | release-dependent |

Typical commands, adapted to actual names:

```bash
./gradlew :sample:<feature>:androidApp:assembleDebug
./gradlew :sample:<feature>:shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosSamples/<Samples>.xcodeproj -scheme <SampleFeature> \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build

adb install -r <sample.apk>
adb shell am start -W -n <application-id>/<activity>
xcrun simctl install booted <sample.app>
xcrun simctl launch --terminate-running-process booted <bundle-id>
```

Inspect logs and visible state after launch. Exercise changed state updates, typed navigation effects,
back behavior, resources, and fake data. After `simctl launch`, wait briefly and confirm that the
process remains alive or that the app is visibly foregrounded; the launch command can return success
even when the process immediately aborts. On failure, inspect the console/crash report before retrying.
Do not report runtime verification from compilation or install/launch exit codes alone.
