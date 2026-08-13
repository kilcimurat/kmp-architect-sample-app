package com.mkilci.kmparchitect.domain.feed

import com.mkilci.kmparchitect.core.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Newest first. This ordering is a product rule, so it lives here rather than in a repository
 * query or a Compose `sortedBy` — both of which would let two callers disagree about it.
 */
class ObserveFeed(
    private val repository: FeedRepository,
) {
    operator fun invoke(): Flow<List<Article>> =
        repository.observeFeed().map { articles ->
            articles.sortedByDescending(Article::publishedAtEpochMillis)
        }
}
