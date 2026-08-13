package com.mkilci.kmparchitect.domain.feed

import com.mkilci.kmparchitect.core.model.Article
import kotlinx.coroutines.flow.Flow

/**
 * The feed port.
 *
 * Offline-first: [observeFeed] is the single observable truth and is backed by local storage.
 * [refresh] synchronises the remote source *into* that storage and reports what happened; it never
 * returns a second list for the UI to reconcile.
 *
 * `Flow`, not `StateFlow`: callers observe changes, and nothing in this contract promises a current
 * value is already available. An implementation is free to keep a hot `StateFlow` internally.
 */
interface FeedRepository {
    fun observeFeed(): Flow<List<Article>>
    suspend fun refresh(): FeedRefreshResult
}

sealed interface FeedRefreshResult {
    data object Refreshed : FeedRefreshResult

    /** Nothing reachable; cached articles remain valid. */
    data object Offline : FeedRefreshResult

    /** The remote answered, but not usably. [reason] is a domain concept, never an HTTP code. */
    data class Failed(val reason: FeedFailure) : FeedRefreshResult
}

enum class FeedFailure {
    RemoteUnavailable,
    MalformedResponse,
}
