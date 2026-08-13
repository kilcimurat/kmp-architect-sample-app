package com.mkilci.kmparchitect.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers are injected rather than referenced directly so tests and fixtures can run work
 * deterministically on a single thread. Nothing below the composition root should touch
 * [Dispatchers] itself.
 */
interface AppDispatchers {
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

class DefaultAppDispatchers : AppDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default

    // Dispatchers.IO does not exist on Kotlin/Native; Default is the correct portable choice here,
    // and platform roots may override this binding when a real IO pool matters.
    override val io: CoroutineDispatcher = Dispatchers.Default
}
