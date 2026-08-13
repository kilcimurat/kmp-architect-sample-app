# Module Blueprints

## Contents

1. Portable target tree
2. Ownership decisions
3. Core, domain, data, presentation, and fixtures
4. Application root
5. Platform capabilities
6. Samples and fakes
7. Tests
8. Migration procedure

## 1. Portable target tree

```text
<repository>/
├── build-logic/
├── app/
│   ├── shared/src/{commonMain,androidMain,iosMain}/
│   └── android/src/main/
├── iosApp/                         # Xcode-owned native executable
├── core/<capability>/src/...       # feature-neutral only
├── domain/<feature>/src/{commonMain,commonTest}/...
├── data/<feature>/src/{commonMain,androidMain,iosMain,commonTest}/...
├── presentation/<feature>/src/{commonMain,commonTest}/...
├── fixtures/<feature>/src/commonMain/...    # deterministic ports, fixed data
└── sample/<feature>/
    ├── shared/src/{commonMain,iosMain,commonTest}/...
    └── androidApp/src/main/...
```

The layer directories are organizational groups; feature leaves are Gradle projects. An iOS sample
executable may live at `sample/<feature>/iosApp` or as a target in one central Xcode sample project.

Adapt packages to the repository. Folder grouping need not dictate package naming as long as imports
and ownership remain clear.

## 2. Ownership decisions

Put a type in the lowest layer that can own it without importing inward-facing implementation/UI
details:

- cross-feature value object: focused `core:model` only if truly feature-neutral;
- feature business model/port/rule/use case: `domain/<feature>`;
- DTO, entity, SDK adapter, mapper: `data/<feature>` or a focused infrastructure module;
- render-only model, screen contract, reducer, ViewModel: `presentation/<feature>`;
- root navigation and cross-feature assembly: `app/shared`;
- platform startup/configuration: native host;
- deterministic feature fake and fixed test data: `fixtures/<feature>`.

Do not create `Utils`, `Manager`, `Base`, giant `Common`, generic repositories, or umbrella modules to
avoid deciding ownership.

## 3. Core, domain, data, presentation, and fixtures

### Core

Create a core module only for a reusable feature-neutral capability. Common examples:

```text
core:common          clocks, dispatchers, small coroutine primitives
core:model           genuinely cross-feature value objects
core:mvi             state/event/effect/store contracts
core:navigation      typed root route contracts
core:designsystem    theme, tokens, reusable UI, shared resources
core:localization    formatting contracts
core:analytics       feature-neutral analytics contract
```

Core must not depend on domain, data, presentation, app, sample, or feature packages.

### Domain

```kotlin
interface ItemRepository {
    fun observeItems(): Flow<List<Item>>
    suspend fun save(item: Item): SaveItemResult
}

class SaveItem(
    private val repository: ItemRepository,
    private val validator: ItemValidator,
) {
    suspend operator fun invoke(draft: ItemDraft): SaveItemResult =
        validator.validate(draft).fold(
            onSuccess = { repository.save(it) },
            onFailure = { SaveItemResult.Invalid(it) },
        )
}
```

Domain is pure Kotlin. It exposes business results rather than HTTP codes, database entities,
Firebase objects, Android resources, or UIKit types. It contains no Koin definitions.

Use `Flow<T>` as the default observable domain contract. Expose `StateFlow<T>` only when callers
intentionally require an always-available current value, synchronous `.value` access, and hot-state
ownership as part of the business API. A data implementation may retain a `StateFlow` internally
while exposing it as `Flow`.

### Data

```kotlin
class DefaultItemRepository(
    private val local: ItemLocalDataSource,
    private val remote: ItemRemoteDataSource,
) : ItemRepository {
    override fun observeItems(): Flow<List<Item>> = local.observeItems()
        .mapState { rows -> rows.map(ItemEntity::toDomain) }

    override suspend fun save(item: Item): SaveItemResult {
        local.upsert(item.toEntity())
        remote.enqueue(item.toPayload())
        return SaveItemResult.Saved
    }
}

val itemDataModule = module {
    single<ItemRepository> { DefaultItemRepository(get(), get()) }
}
```

Data owns implementation bindings. Offline-first repositories observe local state and synchronize
remote changes into it rather than exposing a second UI truth source.

### Presentation

Organize by connected user flow, not one module per screen mechanically:

```text
presentation/<feature>/
├── di/
├── navigation/
└── <screen>/
    ├── model/       State, Action, Event
    ├── navigation/  Effect
    ├── viewmodel/
    └── view/
```

```kotlin
val itemPresentationModule = module {
    viewModel { ItemViewModel(stateStore = ItemStateStore(), observeItems = get(), saveItem = get()) }
}
```

Do not instantiate reusable domain use cases here. The active application/sample composition root
defines them. Presentation may own creation only for a narrowly presentation-specific operation.

Keep route composables responsible for ViewModel/state/effect collection and keep content composables
stateless, callback-driven, previewable, and free from repository lookup.

### Fixtures

```kotlin
// fixtures/<feature>
class FakeItemRepository(
    initialItems: List<Item> = FixedItems.all,
) : ItemRepository {
    private val items = MutableStateFlow(initialItems)
    override fun observeItems(): Flow<List<Item>> = items
    override suspend fun save(item: Item): SaveItemResult {
        items.update { current -> current.filterNot { it.id == item.id } + item }
        return SaveItemResult.Saved
    }
}

object FixedItems {
    val all: List<Item> = listOf(/* stable, human-recognizable data */)
}
```

One deterministic implementation per domain port, written once and consumed by two callers: that
feature's `commonTest` sources and its sample. Duplicating a fake between the two is the problem
this module exists to remove.

Constraints:

- depends only on its own `domain/<feature>` plus core contracts;
- no real I/O, no clock or randomness that is not injected and seeded;
- no `data/*`, no presentation, no other feature;
- consumable only by the same feature's `domain`, `presentation`, and `sample` projects;
- unavailable external operations return explicit safe results rather than throwing generically.

A cross-feature `fakes` or `testing` module that binds many features is the umbrella dependency this
architecture rejects — it silently rebuilds sample graphs into miniature versions of the whole app.
Genuinely feature-neutral helpers belong in a focused `core` testing module instead.

## 4. Application root

`app/shared` is an application root, not a universal shared library. It owns:

- root `App()`/theme surface;
- top-level typed navigation and feature graph composition;
- cross-feature route decisions;
- stable native-facing factory/API for the iOS framework;
- platform-neutral application assembly where genuinely shared.

Features must never depend on `app/shared`. Platform hosts may depend on it. Treat `app/shared` and
the executable hosts as cooperating composition roots: `app/shared` may select shared KMP data
implementations required behind its exported iOS API, while native runtime objects and platform SDK
implementations remain host-owned. Do not force an iOS host to assemble Kotlin objects it cannot
address through the exported framework API.

Composition roots select coherent capability modules; individual definitions remain grouped and
discoverable rather than accumulating in one application-wide DI dumping ground:

```kotlin
val itemUseCaseModule = module {
    factory { ObserveItems(get()) }
    factory { SaveItem(get(), get()) }
}

val productionModules = listOf(
    itemUseCaseModule,
    itemDataModule,
    itemPresentationModule,
    platformModule,
)
```

This is legitimate root assembly, not a global service locator. Keep platform lifecycle objects out
of reusable feature modules.

## 5. Platform capabilities

Use this sequence:

1. If common Kotlin/Compose can implement it, keep it in `commonMain`.
2. If it is a tiny compile-time-equivalent primitive, consider `expect/actual`.
3. If it needs configuration, dependencies, lifecycle, variants, or fakes, define an interface and
   inject platform implementations.
4. If a native framework must initiate it, expose a narrow host callback/factory.

Typical injected capabilities include camera, permissions, notifications, authentication, secure
storage, database builders, share/file pickers, billing, analytics initialization, and app
attestation.

Never pass `Context`, `Activity`, `UIViewController`, `UIApplication`, native navigation controllers,
or SDK singletons through domain APIs.

## 6. Samples and fakes

Each sample shared module owns:

- feature sample root and typed graph;
- the real feature presentation module;
- feature-required reusable domain use case definitions;
- selection of the deterministic ports provided by `fixtures/<feature>`;
- a stable iOS `UIViewController` factory and framework name;
- an `isolationCheck` allowlist naming every project its graph may resolve;
- DI startup tests.

Each native wrapper owns only executable lifecycle and sample-specific native adapters.

A sample must not depend on `data/<feature>`. Binding the real repository drags network,
persistence, serialization, and vendor SDKs back into the feature development loop, which is the
cost this topology exists to remove. Waive it only when persistence or a real integration is the
sample's explicit, documented purpose, and record the waiver in that sample's allowlist.

Never depend on a broad full-app fake module just because it is convenient. That can transitively
bring unrelated features or production notification/auth/network/storage/analytics implementations
into a supposedly isolated sample.

Use fixed fixtures and safe behavior by default. A sample may exercise a production integration only
when integration testing is its explicit purpose and identifiers/external effects are isolated.

## 7. Tests

Place tests beside ownership:

- domain rules/use cases: domain `commonTest`;
- repository mapping/sync: data `commonTest` plus platform tests;
- reducers/ViewModels/effects: presentation `commonTest`, using `fixtures/<feature>`;
- state-store concurrency and effect transport: `core:mvi/commonTest`;
- sample fixture graph startup: sample shared `commonTest`;
- native startup/SDK integration: Android instrumentation and iOS simulator tests.

Test reducers for deterministic old-state-to-new-state transformation and previous-state immutability.
Test ViewModel state and effects independently. Test that one-shot effects are channel-backed and not
replayed. Verify important repository implementations against common contract suites where practical.

## 8. Migration procedure

1. Inventory modules, imports, DI definitions, app/native entry points, tests, and dependency cycles.
2. Write current and target graphs plus a source-to-destination table.
3. Introduce/reuse convention plugins without moving behavior.
4. Type effects before changing navigation ownership; compile affected graphs.
5. Move reusable use-case construction to composition roots; compile production and sample roots.
6. Rename/move app and sample modules only when justified; update Gradle/Xcode/CI atomically.
7. Move fakes into `fixtures/<feature>`, narrow each sample to those fixtures, then declare its
   isolation allowlist and inspect the real resolvable dependency graph against it.
8. Add architecture checks and behavioral tests.
9. Build native executables and perform available runtime checks.
10. Remove legacy paths only after searches and builds prove them unused.

Keep every slice reversible and compilable. Do not perform a repository-wide package rewrite in one
unverified step.
