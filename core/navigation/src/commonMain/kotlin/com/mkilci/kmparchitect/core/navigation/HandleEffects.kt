package com.mkilci.kmparchitect.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mkilci.kmparchitect.core.mvi.ScreenEffect
import kotlinx.coroutines.flow.Flow

/**
 * Collects a screen's typed effects.
 *
 * Generic in `F` on purpose: the route graph receives its own feature's effect type and handles it
 * exhaustively. Nothing here narrows to `ScreenEffect`, so no collector ever needs a cast — the
 * moment a cast appears at a graph boundary, exhaustiveness is gone and a new effect can be added
 * without the compiler noticing.
 */
@Composable
fun <F : ScreenEffect> HandleEffects(
    effects: Flow<F>,
    onEffect: (F) -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect(onEffect)
    }
}
