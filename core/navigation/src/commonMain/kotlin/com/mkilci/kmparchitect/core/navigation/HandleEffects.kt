package com.mkilci.kmparchitect.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mkilci.kmparchitect.core.mvi.ScreenEffect
import kotlinx.coroutines.flow.Flow

/**
 * Collects a screen's typed effects.
 *
 * Generic in `F` on purpose: the route graph receives its own feature's effect type and handles it
 * exhaustively. Nothing here narrows to `ScreenEffect`, so no collector ever needs a cast — the
 * moment a cast appears at a graph boundary, exhaustiveness is gone and a new effect can be added
 * without the compiler noticing.
 *
 * Collection is bound to `STARTED` rather than to composition. A composition outlives the screen
 * being visible, so collecting for its whole life delivers a navigation command to a host that has
 * already stopped — the same class of bug as replaying an effect from state, arriving from the
 * other direction. Pausing is safe here precisely because the transport is a buffered channel:
 * an effect emitted while stopped waits in the channel and is delivered on the next `STARTED`,
 * rather than being dropped or replayed.
 */
@Composable
fun <F : ScreenEffect> HandleEffects(
    effects: Flow<F>,
    onEffect: (F) -> Unit,
) {
    val currentOnEffect by rememberUpdatedState(onEffect)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect -> currentOnEffect(effect) }
        }
    }
}
