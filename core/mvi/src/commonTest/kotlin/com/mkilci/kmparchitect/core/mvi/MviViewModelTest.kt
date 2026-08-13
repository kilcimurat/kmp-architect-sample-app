package com.mkilci.kmparchitect.core.mvi

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals as assertEq
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private data class ProbeState(val value: String = "initial") : ScreenState

private sealed interface ProbeEvent : ScreenEvent<ProbeState> {
    data class Changed(val value: String) : ProbeEvent {
        override fun reduce(oldState: ProbeState) = oldState.copy(value = value)
    }
}

private sealed interface ProbeEffect : ScreenEffect {
    data class Navigated(val target: String) : ProbeEffect
    data object Dismissed : ProbeEffect
}

private class ProbeViewModel : MviViewModel<ProbeState, ProbeEvent, ProbeEffect>(
    DefaultStateStore(ProbeState()),
) {
    /** Captured at the moment the transport is closed, to pin the teardown ordering. */
    var scopeAlreadyCancelledWhenTransportClosed: Boolean? = null
        private set

    fun emit(effect: ProbeEffect) = sendEffect(effect)

    override fun onCleared() {
        scopeAlreadyCancelledWhenTransportClosed = !viewModelScope.isActive
        super.onCleared()
    }
}

/** Tears the ViewModel down the way the framework does, through a real [ViewModelStore]. */
private fun ProbeViewModel.clearThroughStore() {
    val store = ViewModelStore()
    store.put("probe", this)
    store.clear()
}

class MviViewModelTest {

    @Test
    fun an_action_result_reaches_state_through_event_reduction() {
        val viewModel = ProbeViewModel()

        viewModel.sendEvent(ProbeEvent.Changed("loaded"))

        assertEquals("loaded", viewModel.state.value.value)
    }

    @Test
    fun effects_are_delivered_independently_of_state() = runTest {
        val viewModel = ProbeViewModel()

        viewModel.emit(ProbeEffect.Navigated("details"))

        val effect = withTimeout(1_000) { viewModel.effects.first() }
        assertEq(ProbeEffect.Navigated("details"), effect)
        assertEquals("initial", viewModel.state.value.value, "an effect must not mutate state")
    }

    @Test
    fun effects_buffered_before_collection_are_delivered_once_and_not_replayed() = runTest {
        val viewModel = ProbeViewModel()

        viewModel.emit(ProbeEffect.Navigated("first"))
        viewModel.emit(ProbeEffect.Dismissed)

        val firstCollector = withTimeout(1_000) {
            listOf(viewModel.effects.first(), viewModel.effects.first())
        }
        assertEquals(listOf(ProbeEffect.Navigated("first"), ProbeEffect.Dismissed), firstCollector)

        // A second collector must not see already consumed navigation. Closing the transport lets
        // toList() terminate; without the close it would suspend forever, which is itself the proof
        // that nothing was replayed.
        viewModel.clearThroughStore()
        val secondCollector = withTimeout(1_000) { viewModel.effects.toList() }
        assertTrue(secondCollector.isEmpty(), "consumed effects were replayed: $secondCollector")
    }

    @Test
    fun sending_on_a_closed_transport_fails_fast_instead_of_dropping_silently() {
        val viewModel = ProbeViewModel()
        viewModel.clearThroughStore()

        val failure = assertFailsWith<IllegalStateException> {
            viewModel.emit(ProbeEffect.Dismissed)
        }
        assertTrue(
            failure.message.orEmpty().contains("closed=true"),
            "rejection should name the transport state, was: ${failure.message}",
        )
    }

    @Test
    fun sending_on_a_full_transport_fails_fast_instead_of_dropping_silently() {
        val viewModel = ProbeViewModel()

        val failure = (0 until 10_000).firstNotNullOfOrNull { index ->
            runCatching { viewModel.emit(ProbeEffect.Navigated(index.toString())) }.exceptionOrNull()
        }

        assertNotNull(failure, "the buffered transport never reported capacity exhaustion")
        assertTrue(failure is IllegalStateException)
        assertTrue(
            failure.message.orEmpty().contains("closed=false"),
            "a full active channel should be distinguished from a closed channel: ${failure.message}",
        )
        viewModel.clearThroughStore()
    }

    @Test
    fun viewModelScope_is_cancelled_before_the_effect_transport_closes() {
        // This is what makes the fail-fast policy safe rather than a crash: by the time the channel
        // closes, no coroutine owned by this ViewModel can still be running to send into it.
        val viewModel = ProbeViewModel()
        val store = ViewModelStore()
        store.put("probe", viewModel)

        assertTrue(viewModel.viewModelScope.isActive)

        store.clear()

        assertNotNull(viewModel.scopeAlreadyCancelledWhenTransportClosed)
        assertTrue(
            viewModel.scopeAlreadyCancelledWhenTransportClosed == true,
            "viewModelScope was still active when the effect transport closed — the fail-fast " +
                "policy would then be reachable during ordinary teardown",
        )
    }
}
