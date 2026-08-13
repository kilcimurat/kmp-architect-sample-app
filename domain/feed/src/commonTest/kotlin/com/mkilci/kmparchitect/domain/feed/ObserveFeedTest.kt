package com.mkilci.kmparchitect.domain.feed

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Domain tests stub their own ports inline. They deliberately do not use `fixtures:feed`: that
 * module implements this module's ports, so depending on it — even from a test source set — is a
 * Gradle project cycle.
 */
private class StubFeedRepository(
    private val articles: List<Article>,
    private val refreshResult: FeedRefreshResult = FeedRefreshResult.Refreshed,
) : FeedRepository {
    var refreshCount: Int = 0
        private set

    override fun observeFeed(): Flow<List<Article>> = flowOf(articles)

    override suspend fun refresh(): FeedRefreshResult {
        refreshCount++
        return refreshResult
    }
}

private fun article(id: String, publishedAt: Long) = Article(
    id = ArticleId(id),
    title = "Article $id",
    summary = "Summary $id",
    source = "Test Source",
    url = "https://example.test/$id",
    publishedAtEpochMillis = publishedAt,
)

class ObserveFeedTest {

    @Test
    fun articles_are_ordered_newest_first() = runTest {
        val repository = StubFeedRepository(
            listOf(
                article("older", publishedAt = 1_000),
                article("newest", publishedAt = 3_000),
                article("middle", publishedAt = 2_000),
            ),
        )

        val result = ObserveFeed(repository).invoke().first()

        assertEquals(listOf("newest", "middle", "older"), result.map { it.id.value })
    }

    @Test
    fun an_empty_feed_stays_empty_rather_than_failing() = runTest {
        val result = ObserveFeed(StubFeedRepository(emptyList())).invoke().first()

        assertEquals(emptyList(), result)
    }
}

class RefreshFeedTest {

    @Test
    fun refresh_delegates_to_the_repository_and_returns_its_domain_result() = runTest {
        val repository = StubFeedRepository(emptyList(), FeedRefreshResult.Offline)

        val result = RefreshFeed(repository).invoke()

        assertEquals(FeedRefreshResult.Offline, result)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun a_remote_failure_surfaces_as_a_domain_reason_not_a_transport_code() = runTest {
        val repository = StubFeedRepository(
            emptyList(),
            FeedRefreshResult.Failed(FeedFailure.MalformedResponse),
        )

        val result = RefreshFeed(repository).invoke()

        assertEquals(FeedRefreshResult.Failed(FeedFailure.MalformedResponse), result)
    }
}
