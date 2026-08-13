# MVI, Typed Navigation, and DI

## Contents

1. Contracts and atomic state
2. Typed ViewModel effects
3. Action/Event/Effect flow
4. Compose and navigation ownership
5. Koin ownership
6. Composition roots
7. Tests and failure patterns

## 1. Contracts and atomic state

```kotlin
interface ScreenState

interface ScreenEvent<S : ScreenState> {
    fun reduce(oldState: S): S
}

interface ScreenEffect

interface StateStore<S : ScreenState, E : ScreenEvent<S>> {
    val state: StateFlow<S>
    fun sendEvent(event: E)
}

open class DefaultStateStore<S : ScreenState, E : ScreenEvent<S>>(
    initialState: S,
) : StateStore<S, E> {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<S> = mutableState.asStateFlow()

    override fun sendEvent(event: E) {
        mutableState.update { current -> event.reduce(current) }
    }
}
```

`MutableStateFlow.update` may invoke a reducer more than once under contention. Therefore every
reducer must be pure, synchronous, deterministic for its inputs, and free from mutation.

Reducers must not launch coroutines, read clocks/randomness implicitly, call repositories, emit
analytics/effects, navigate, or mutate a collection owned by the previous state.

## 2. Typed ViewModel effects

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

    override fun onCleared() {
        effectChannel.close()
        super.onCleared()
    }
}
```

Do not expose `Flow<ScreenEffect>` from feature ViewModels. Do not cast effects at graph boundaries.
Each screen/feature declares its own sealed effect hierarchy, including its own back effect when
needed.

A buffered channel handles short collector gaps without replaying already consumed navigation. The
policy above expects acceptance while the ViewModel transport is usable and fails fast on a closed or
full channel; it never silently ignores `trySend`. It is process-memory delivery only, with no durable
process-death guarantee. Persist genuine business state, not navigation commands.

Fail-fast is only defensible if normal teardown cannot trigger it. State the rule in terms of scope
ownership, not of coroutines:

- a **synchronous** Effect may be emitted directly from a ViewModel-owned method. `onAction` calling
  `sendEffect` is the normal case and needs no coroutine;
- an **asynchronous** Effect must originate from `viewModelScope`, or another scope the ViewModel
  owns, which is cancelled before `onCleared()` closes the channel;
- an Effect must never originate from an external or unowned scope.

Under those three, a rejection means a real defect rather than a routine lifecycle race. Test the
ordering explicitly; an unbounded `check` that can fire while a screen is being dismissed is a crash,
not a guardrail. Consider a build-type-aware policy only if production telemetry later shows a path
this ordering does not cover, and document it if so.

## 3. Action/Event/Effect flow

```kotlin
data class ItemState(
    val items: List<ItemUi> = emptyList(),
    val isLoading: Boolean = true,
) : ScreenState

sealed interface ItemAction {
    data class ItemClicked(val id: String) : ItemAction
    data object RetryClicked : ItemAction
    data object BackClicked : ItemAction
}

sealed interface ItemEvent : ScreenEvent<ItemState> {
    data class Loaded(val items: List<ItemUi>) : ItemEvent {
        override fun reduce(oldState: ItemState) =
            oldState.copy(items = items, isLoading = false)
    }
}

sealed interface ItemEffect : ScreenEffect {
    data class OpenDetails(val id: String) : ItemEffect
    data object NavigateBack : ItemEffect
}
```

```kotlin
class ItemViewModel(
    stateStore: ItemStateStore,
    private val observeItems: ObserveItems,
) : MviViewModel<ItemState, ItemEvent, ItemEffect>(stateStore) {

    init {
        viewModelScope.launch {
            observeItems().collect { items ->
                sendEvent(ItemEvent.Loaded(items.map(Item::toUi)))
            }
        }
    }

    fun onAction(action: ItemAction) {
        when (action) {
            is ItemAction.ItemClicked -> sendEffect(ItemEffect.OpenDetails(action.id))
            ItemAction.BackClicked -> sendEffect(ItemEffect.NavigateBack)
            ItemAction.RetryClicked -> refresh()
        }
    }
}
```

Action is UI intent. Event is a state transition. Effect is a one-shot external request. Keep them
separate even when a screen is small. Async/business orchestration belongs in ViewModel/use cases;
completed results become Events.

## 4. Compose and navigation ownership

Use typed routes:

```kotlin
@Serializable data object ItemListRoute
@Serializable data class ItemDetailsRoute(val itemId: String)
```

Keep effect collection generic and typed:

```kotlin
@Composable
fun <F : ScreenEffect> HandleEffects(
    effects: Flow<F>,
    onEffect: (F) -> Unit,
) {
    LaunchedEffect(effects) { effects.collect(onEffect) }
}
```

The graph owns the navigation controller:

```kotlin
fun NavGraphBuilder.itemGraph(navController: NavHostController) {
    composable<ItemListRoute> {
        val viewModel = koinViewModel<ItemViewModel>()
        HandleEffects(viewModel.effects) { effect ->
            when (effect) {
                ItemEffect.NavigateBack -> navController.popBackStack()
                is ItemEffect.OpenDetails ->
                    navController.navigate(ItemDetailsRoute(effect.id))
            }
        }
        ItemRouteScreen(viewModel)
    }
}
```

ViewModels must not retain `NavController`, `Activity`, `UIViewController`, native navigators, or
route graph objects. Pass stable identifiers through routes and reload domain data through use cases.

Route-level composables may resolve ViewModels. Content composables receive immutable state and
callbacks; they do not resolve repositories or navigate directly.

## 5. Koin ownership

The container is runtime by default, and the reason is measured rather than assumed: this
implementation keeps a leaf-feature edit inside the leaf, with no generated code between a feature
and its consumers. The cost is that a mis-wired graph fails at startup instead of at compile time,
so every composition root owes a graph-startup test; treat that test as the price of the choice, not
as optional coverage.

The rule being enforced is about build behaviour, not about tooling category. Compile-time DI is not
forbidden. What is forbidden is any strategy that puts aggregating work in `app/*` or makes a
one-line change in a leaf feature reprocess unrelated modules. Some aggregation models do that;
compiler-plugin approaches may not. Before swapping the container, inspect the real task graph and
time a leaf-feature edit — then decide.

Definitions live near what they implement, while selection/assembly belongs to a root:

```kotlin
// data/<feature>
val itemDataModule = module {
    single<ItemRepository> { DefaultItemRepository(get(), get()) }
}

// presentation/<feature>
val itemPresentationModule = module {
    viewModel { ItemViewModel(stateStore = ItemStateStore(), observeItems = get()) }
}

// application/sample composition root, grouped by capability
val inventoryUseCaseModule = module {
    factory { ObserveItems(get()) }
    factory { SaveItem(get(), get()) }
}
```

Rules:

- Domain imports no Koin and constructors remain directly callable.
- Data owns its implementation bindings, not use cases it merely supplies repositories to.
- Presentation owns ViewModel definitions, not reusable domain operations.
- Composition roots own cross-layer object construction and active module selection. Keep individual
  UseCase definitions discoverable in capability modules instead of one giant root module.
- A genuinely presentation-specific helper/use case may be constructed near presentation, but make
  the exception explicit.
- Use `single` for real shared/application lifetime, `factory` for cheap operations, `viewModel` for
  ViewModels, and scopes only for tested lifecycles.

Qualifiers express semantic variants, not unclear ownership. Prefer typed configuration wrappers over
raw string qualifiers.

## 6. Composition roots

Android may start process-safe definitions in `Application` and complete Activity/suspend-dependent
assembly in an explicit pre-render bootstrap:

```kotlin
class ProductApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ProductApplication)
            modules(processModules)
        }
    }
}
```

iOS exposes a small stable entry point:

```kotlin
fun ProductAppViewController(): UIViewController {
    startProductKoinIfNeeded(productionModules + iosPlatformModules)
    return ComposeUIViewController { App() }
}
```

Sample roots assemble only feature-required use cases, presentation definitions, and the
deterministic implementations provided by `fixtures/<feature>`. They never bind `data/<feature>`.
Never call `startKoin` inside a reusable screen, ViewModel, or feature module.

Keep SDK initialization ordered and explicit. Do not hide unavailable Activity/native dependencies
behind fake process-level objects.

## 7. Tests and failure patterns

Required MVI tests:

- reducer produces the expected state from a known old state;
- the old state and owned collections remain unchanged;
- repeated reduction with the same inputs is deterministic;
- concurrent `StateStore.sendEvent` calls do not lose updates;
- repository/use-case output reaches expected state via Event reduction;
- actions emit expected typed effects independently from state;
- consumed one-shot effects do not replay;
- closed/full Effect transport follows the documented fail-fast policy;
- clearing the ViewModel cancels `viewModelScope` before closing the transport, so ordinary teardown
  never trips the fail-fast rejection;
- sample DI graph starts and resolves the initial ViewModel and fixture data.

Reject:

```kotlin
class BadViewModel(val navController: NavController)
class BadViewModel(val repository: DefaultItemRepository)
val effects = MutableStateFlow<ItemEffect?>(null)
val repository = getKoin().get<ItemRepository>() // inside a composable

override fun reduce(oldState: State): State {
    repository.save() // side effect in reducer
    return oldState
}
```

Also reject global effects/events shared by unrelated features, mutable lists inside state, exception
messages as production copy, and casts in effect collectors.
