package com.mkilci.kmparchitect.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Typed MVI ViewModel: one screen, one State, one Event hierarchy, one Effect hierarchy.
 *
 * Effects travel on a buffered [Channel] rather than in state. A consumed navigation command must
 * not replay when the screen is recreated, which is exactly what a replaying state holder would do.
 *
 * Delivery is in-process and non-durable. There is no process-death guarantee — persist business
 * state, never navigation commands.
 *
 * **Fail-fast, and why it is safe.** [sendEffect] rejects loudly instead of dropping silently. The
 * rule that makes that defensible is about scope ownership, not about coroutines:
 *
 * - a synchronous effect may be emitted directly from a ViewModel-owned method — `onAction` calling
 *   [sendEffect] is the normal case and needs no coroutine;
 * - an asynchronous effect must originate from `viewModelScope`, or another scope this ViewModel
 *   owns, which `clear()` cancels *before* calling [onCleared] and closing the transport;
 * - an effect must never originate from an external or unowned scope.
 *
 * Under those three, a rejection means a real defect rather than a routine teardown race.
 * `MviViewModelTest` pins the ordering so a lifecycle change in a future androidx release cannot
 * quietly turn it into a crash.
 */
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
