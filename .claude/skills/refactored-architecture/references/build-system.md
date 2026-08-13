# Build System Reference

## Contents

1. Compatibility and discovery
2. Settings topology
3. Convention plugins
4. KMP library and Compose conventions
5. Application and sample modules
6. Build-isolation levers
7. Framework naming and Xcode embedding
8. Build-system validation

## 1. Compatibility and discovery

Never treat versions in an example as current defaults. Before editing, read:

- `gradle/wrapper/gradle-wrapper.properties`;
- `gradle/libs.versions.toml` or equivalent;
- root and included-build plugin declarations;
- Android compile/min/target SDK values;
- Kotlin/Compose/AGP/Koin compatibility constraints;
- CI Java/Xcode/macOS versions.

For an existing repository, preserve its working matrix unless an upgrade is explicitly in scope. For
a greenfield repository, verify official compatibility sources and pin one coherent matrix. Upgrade
one boundary at a time and compile Android plus supported Apple architectures after each change.

Modern AGP separates these roles:

- KMP libraries: `org.jetbrains.kotlin.multiplatform` plus the supported Android KMP library plugin;
- Android executables: `com.android.application`;
- never combine a KMP library and Android application role in one Gradle project when the selected
  AGP forbids it.

Adapt to the project's selected AGP rather than blindly applying AGP-specific snippets.

## 2. Settings topology

Preferred paths:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "<project-name>"

include(":app:android", ":app:shared")
include(":core:common", ":core:model", ":core:mvi", ":core:navigation")
include(":domain:<feature>", ":data:<feature>", ":presentation:<feature>")
include(":fixtures:<feature>")
include(":sample:<feature>:shared", ":sample:<feature>:androidApp")
```

Register only existing modules. A native Xcode host normally stays outside Gradle. A centralized
`iosSamples` Xcode project may own executable targets for multiple `sample/<feature>/shared`
frameworks.

Do not infer a Gradle project path from its old directory name after moving it. Update:

- `settings.gradle(.kts)` includes;
- every `project(":...")` dependency;
- task strings in scripts and CI;
- Xcode framework search paths and build phases;
- signing/config file paths calculated relative to moved Android modules.

## 3. Convention plugins

Use an included `build-logic` build when several modules repeat target, Compose, testing, namespace,
or framework configuration.

Recommended capabilities:

```text
<prefix>.kmp.library       Android+iOS KMP targets and common tests
<prefix>.kmp.compose       library convention plus Compose dependencies/plugins
<prefix>.kmp.framework     static Apple framework configuration
<prefix>.android.app       shared Android executable configuration when repetition justifies it
<prefix>.architecture      root architectureCheck and isolationCheck tasks
```

Use a project-specific prefix. Do not copy a sample prefix. Prefer typed Gradle APIs and lazy
providers. Avoid `afterEvaluate` unless the selected plugin API leaves no stable alternative.

Keep architecture-rule logic pure enough to unit test independently from Gradle execution.

Keep the included build **small and stable**. Every edit to build logic invalidates configuration
for every project, so a convention plugin that changes weekly costs more than the duplication it
removed. Prefer few plugins with narrow responsibilities, and keep frequently-tuned rule data
(allowlists, forbidden markers) in files the tasks read as inputs rather than in plugin source.

## 4. KMP library and Compose conventions

The base KMP convention owns the supported targets once:

```kotlin
extensions.configure<KotlinMultiplatformExtension> {
    // Configure the Android KMP library extension supported by the selected AGP.
    iosArm64()
    iosSimulatorArm64()

    sourceSets.commonMain.dependencies {
        implementation(libs.findLibrary("kotlinx-coroutines-core").get())
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
    }
}
```

Add only targets required by the product. Do not add desktop or Intel iOS simulator targets without
need and artifact support.

The Compose convention applies the base convention plus Compose/compiler plugins and truly common UI
dependencies. Do not force navigation, Koin Compose, or lifecycle libraries into every Compose
module unless every module actually uses them.

Namespace generation must be deterministic and valid. Either require an explicit namespace or
derive it from the Gradle path with a tested transformation.

## 5. Application and sample modules

Production Android host:

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

android {
    namespace = "<base-package>"
    defaultConfig {
        applicationId = "<application-id>"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
}

dependencies {
    implementation(project(":app:shared"))
    // Add only platform/data implementations selected by this composition root.
}
```

Feature presentation module:

```kotlin
plugins { id("<prefix>.kmp.compose") }

kotlin.sourceSets.commonMain.dependencies {
    implementation(project(":domain:<feature>"))
    implementation(project(":core:mvi"))
    implementation(project(":core:designsystem"))
}
```

Feature fixtures module:

```kotlin
plugins { id("<prefix>.kmp.library") }

kotlin.sourceSets.commonMain.dependencies {
    implementation(project(":domain:<feature>"))
    // No data, no presentation, no other feature, no Compose.
}
```

Feature sample shared module:

```kotlin
plugins { id("<prefix>.kmp.framework") }

kotlin.sourceSets.commonMain.dependencies {
    implementation(project(":presentation:<feature>"))
    implementation(project(":domain:<feature>"))
    implementation(project(":fixtures:<feature>"))
    // Deliberately absent: :data:<feature>, :app:*, every other feature.
}
```

Android sample executable:

```kotlin
plugins { alias(libs.plugins.androidApplication) }

dependencies {
    implementation(project(":sample:<feature>:shared"))
}
```

Do not make isolated samples depend on a broad `sample:shared` graph that binds unrelated production
implementations. Share only genuinely feature-neutral test helpers; keep fake repositories close to
their sample.

Default to `implementation`. Use `api` only when public types intentionally expose that dependency.

## 6. Build-isolation levers

Module boundaries decide what *can* be skipped; these settings decide what actually is.

**Dependency shape.** `implementation` everywhere except deliberate API re-export. An `api` edge
means every consumer recompiles when the re-exported ABI changes, so each one needs a recorded
justification and belongs in the architecture check's allowlist.

**No aggregating codegen in the feature path.** Annotation processors that collect a component in
the application module reprocess it on every feature change. If a compile-time DI framework is
introduced later, verify that a leaf-feature edit does not trigger work in `app/*` before accepting
it.

**Configuration cost scales with module count.** Isolation shortens compilation but lengthens
configuration; a 30-project build configures 30 projects whatever you compile. Keep the
configuration cache enabled and treat configuration time as a tracked metric, not a footnote.

**Gradle Isolated Projects.** Isolated Projects configures projects in parallel and is the direct
countermeasure to the configuration cost above.

Its status moves quickly and differs between the release notes and the user guide at any given
moment, so **verify the current status and plugin compatibility against the official Gradle
documentation for the version you are on** rather than trusting a status recorded here. As of the
Gradle 9.7.0 release notes it "transitions from being experimental to incubating", is not enabled by
default, and is "not yet recommended for production use".

Treat it as an optional, measured optimisation — never an architectural requirement. Evaluate it
explicitly: enable it, measure configuration time, and if plugins are incompatible, record which
ones failed and revert rather than leaving a half-working flag in `gradle.properties`.

**Task path discipline.** The daily loop should be a single sample task
(`:sample:<feature>:androidApp:installDebug`). Verify that this invocation configures the expected
projects and compiles nothing from `data/*`, `app/*`, or other features — if it does, the topology
is not delivering what it claims regardless of how the diagram looks.

## 7. Framework naming and Xcode embedding

Configure frameworks on existing native targets:

```kotlin
targets.withType<KotlinNativeTarget>().configureEach {
    binaries.framework {
        baseName = providers.gradleProperty("frameworkBaseName")
            .orElse(project.name.toFrameworkName())
            .get()
        isStatic = true
    }
}
```

Framework names must be stable, unique, and valid Swift identifiers. A nested module named `shared`
cannot derive a unique framework name from `project.name` alone; derive from the parent feature or set
it explicitly.

An Xcode build phase should call the matching Gradle embed/link task using repository-relative paths.
Never hard-code a developer-machine absolute path. If XcodeGen is used, edit `project.yml` and
regenerate the project instead of hand-editing generated settings.

## 8. Build-system validation

Discover exact task names with `./gradlew projects` and `./gradlew tasks`. Then run applicable gates:

```bash
./gradlew architectureCheck
./gradlew isolationCheck
./gradlew check
./gradlew :app:android:assembleDebug
./gradlew :app:shared:compileKotlinIosSimulatorArm64
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample:<feature>:androidApp:assembleDebug
./gradlew :sample:<feature>:shared:linkDebugFrameworkIosSimulatorArm64
```

The final gate order is: implementation tests and `architectureCheck`, documentation synchronization,
production Android build, production iOS framework plus native executable build, two isolated Android
samples, two isolated iOS frameworks plus native executables, and only then build-isolation
benchmarks. A framework-only Apple build is not an executable verification result.

`architectureCheck` must be backed by tests that demonstrate valid structures pass and invalid
fixtures fail, including domain-to-data/presentation, presentation-to-data, core-to-feature, and
sample-to-application-root edges. Source guardrails should inspect actual `ScreenEvent.reduce`
implementations rather than reducer filenames and must be described as guardrails, not a proof of
semantic purity.

Also verify:

- every sample's resolved graph matches its declared allowlist, with no `data/*`, `app/*`, or
  foreign-feature nodes;
- no incompatible plugin combination;
- unique namespaces, application IDs, bundle IDs, and framework names;
- Xcode selects the correct framework for `SDK_NAME`/architecture;
- root path changes did not break signing, service config, assets, or generated artifacts;
- configuration cache works for custom verification tasks when the project enables it.
