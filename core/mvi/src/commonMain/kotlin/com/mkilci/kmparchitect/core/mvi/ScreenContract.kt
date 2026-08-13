package com.mkilci.kmparchitect.core.mvi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything a screen renders. Immutable; owned collections are never mutated in place. */
interface ScreenState

/**
 * A state transition. [reduce] must be pure, synchronous and deterministic:
 * [MutableStateFlow.update] may invoke it more than once under contention.
 */
interface ScreenEvent<S : ScreenState> {
    fun reduce(oldState: S): S
}

/** A one-shot request to something outside the screen — navigation, sharing, a system dialog. */
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
