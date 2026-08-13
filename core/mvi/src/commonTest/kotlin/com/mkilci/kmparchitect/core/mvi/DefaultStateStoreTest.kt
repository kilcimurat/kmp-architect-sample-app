package com.mkilci.kmparchitect.core.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class CounterState(
    val count: Int = 0,
    val labels: List<String> = emptyList(),
) : ScreenState

private sealed interface CounterEvent : ScreenEvent<CounterState> {

    data object Incremented : CounterEvent {
        override fun reduce(oldState: CounterState) = oldState.copy(count = oldState.count + 1)
    }

    data class LabelAdded(val label: String) : CounterEvent {
        override fun reduce(oldState: CounterState) =
            oldState.copy(labels = oldState.labels + label)
    }
}

class DefaultStateStoreTest {

    @Test
    fun reduce_maps_a_known_old_state_to_the_expected_new_state() {
        val store = DefaultStateStore<CounterState, CounterEvent>(CounterState(count = 4))

        store.sendEvent(CounterEvent.Incremented)

        assertEquals(5, store.state.value.count)
    }

    @Test
    fun reduce_is_deterministic_when_applied_repeatedly_to_the_same_input() {
        val oldState = CounterState(count = 1, labels = listOf("a"))

        val first = CounterEvent.LabelAdded("b").reduce(oldState)
        val second = CounterEvent.LabelAdded("b").reduce(oldState)

        assertEquals(first, second)
    }

    @Test
    fun reduce_leaves_the_previous_state_and_its_collections_untouched() {
        val oldState = CounterState(count = 1, labels = listOf("a"))
        val oldLabels = oldState.labels

        val newState = CounterEvent.LabelAdded("b").reduce(oldState)

        assertEquals(listOf("a"), oldState.labels)
        assertSame(oldLabels, oldState.labels)
        assertEquals(listOf("a", "b"), newState.labels)
        assertEquals(1, oldState.count)
    }

    @Test
    fun concurrent_events_do_not_lose_updates() = runTest {
        val store = DefaultStateStore<CounterState, CounterEvent>(CounterState())
        val eventCount = 1_000

        withContext(Dispatchers.Default) {
            (1..eventCount)
                .map { async { store.sendEvent(CounterEvent.Incremented) } }
                .awaitAll()
        }

        assertEquals(eventCount, store.state.value.count)
    }

    @Test
    fun state_is_exposed_as_a_read_only_stream() {
        val store = DefaultStateStore<CounterState, CounterEvent>(CounterState())

        // A caller must not be able to push state in behind the reducer.
        assertTrue(store.state !is kotlinx.coroutines.flow.MutableStateFlow<*>)
    }
}
