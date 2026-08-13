package com.mkilci.kmparchitect.core.common

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Time is a port, not an ambient call. Reducers must be deterministic and fixtures must produce the
 * same screen on every run, which is impossible if any layer can read the system clock directly.
 *
 * `kotlinx.datetime.Clock` was removed in datetime 0.8.0; the standard library clock is the
 * replacement.
 */
fun interface TimeProvider {
    fun nowEpochMillis(): Long
}

@OptIn(ExperimentalTime::class)
class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/** Fixed time for tests and samples. */
class FixedTimeProvider(private val epochMillis: Long) : TimeProvider {
    override fun nowEpochMillis(): Long = epochMillis
}
