package com.mkilci.kmparchitect.fixtures.feed

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import com.mkilci.kmparchitect.domain.feed.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic [FeedRepository]. No network, no disk, no clock.
 *
 * It keeps a hot `MutableStateFlow` internally while exposing `Flow` — the same shape the real
 * repository has, so a screen cannot behave differently against the fixture than against
 * production. [refreshResults] lets a sample or a test walk through refresh outcomes in a fixed
 * order instead of relying on chance.
 */
class FakeFeedRepository(
    initialArticles: List<Article> = FeedFixtures.articles,
    private val refreshResults: List<FeedRefreshResult> = listOf(FeedRefreshResult.Refreshed),
    private val articlesAddedOnRefresh: List<Article> = emptyList(),
) : FeedRepository {

    private val articles = MutableStateFlow(initialArticles)
    private var refreshIndex = 0

    var refreshCount: Int = 0
        private set

    override fun observeFeed(): Flow<List<Article>> = articles

    override suspend fun refresh(): FeedRefreshResult {
        refreshCount++
        val result = refreshResults[refreshIndex.coerceAtMost(refreshResults.lastIndex)]
        refreshIndex++

        if (result is FeedRefreshResult.Refreshed && articlesAddedOnRefresh.isNotEmpty()) {
            articles.update { current ->
                val existingIds = current.mapTo(mutableSetOf()) { it.id }
                current + articlesAddedOnRefresh.filterNot { it.id in existingIds }
            }
        }
        return result
    }
}
