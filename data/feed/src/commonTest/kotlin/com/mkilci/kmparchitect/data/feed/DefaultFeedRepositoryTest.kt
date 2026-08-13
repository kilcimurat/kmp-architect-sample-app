package com.mkilci.kmparchitect.data.feed

import com.mkilci.kmparchitect.core.network.NetworkConfig
import com.mkilci.kmparchitect.core.network.createHttpClient
import com.mkilci.kmparchitect.data.articlestore.ArticleLocalStore
import com.mkilci.kmparchitect.data.articlestore.ArticleRecord
import com.mkilci.kmparchitect.domain.feed.FeedFailure
import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * In-memory store so the repository's sync behaviour is testable in `commonTest` without a SQLite
 * driver. The SQLDelight implementation is exercised separately on a real device/host.
 */
private class InMemoryArticleLocalStore(
    initial: List<ArticleRecord> = emptyList(),
) : ArticleLocalStore {

    private val records = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<ArticleRecord>> = records
    override fun observeById(id: String): Flow<ArticleRecord?> = records.map { all -> all.find { it.id == id } }
    override fun observeBookmarked(): Flow<List<ArticleRecord>> = records.map { all -> all.filter { it.bookmarked } }

    override suspend fun upsertAll(records: List<ArticleRecord>) {
        this.records.update { current ->
            val incoming = records.associateBy { it.id }
            val merged = current.map { existing ->
                // Mirrors the SQL upsert: a refresh updates content but preserves the bookmark flag.
                incoming[existing.id]?.copy(bookmarked = existing.bookmarked) ?: existing
            }
            merged + records.filterNot { new -> current.any { it.id == new.id } }
        }
    }

    override suspend fun setBookmarked(id: String, bookmarked: Boolean) {
        records.update { all -> all.map { if (it.id == id) it.copy(bookmarked = bookmarked) else it } }
    }
}

private fun record(id: String, title: String = "Title $id", bookmarked: Boolean = false) = ArticleRecord(
    id = id,
    title = title,
    summary = "Summary $id",
    source = "Source",
    url = "https://example.test/$id",
    publishedAtEpochMillis = 1_000,
    bookmarked = bookmarked,
)

private fun remoteReturning(payload: String) = FeedRemoteDataSource(
    client = createHttpClient(
        MockEngine {
            respond(
                content = payload,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ),
    config = NetworkConfig(baseUrl = "https://example.test"),
)

class DefaultFeedRepositoryTest {

    @Test
    fun the_observed_feed_comes_from_local_storage() = runTest {
        val local = InMemoryArticleLocalStore(listOf(record("a"), record("b")))
        val repository = DefaultFeedRepository(local, remoteReturning("[]"))

        val articles = repository.observeFeed().first()

        assertEquals(listOf("a", "b"), articles.map { it.id.value })
    }

    @Test
    fun a_successful_refresh_synchronises_the_remote_response_into_local_storage() = runTest {
        val local = InMemoryArticleLocalStore()
        val repository = DefaultFeedRepository(
            local,
            remoteReturning(
                """[{"id":"a","title":"Remote A","summary":"s","source":"Src","url":"u","publishedAt":10}]""",
            ),
        )

        val result = repository.refresh()

        assertEquals(FeedRefreshResult.Refreshed, result)
        assertEquals(listOf("Remote A"), repository.observeFeed().first().map { it.title })
    }

    @Test
    fun a_refresh_updates_content_without_clearing_a_bookmark() = runTest {
        val local = InMemoryArticleLocalStore(listOf(record("a", title = "Old", bookmarked = true)))
        val repository = DefaultFeedRepository(
            local,
            remoteReturning(
                """[{"id":"a","title":"New","summary":"s","source":"Src","url":"u","publishedAt":10}]""",
            ),
        )

        repository.refresh()

        val stored = local.observeAll().first().single()
        assertEquals("New", stored.title)
        assertTrue(stored.bookmarked, "a feed refresh must never silently un-bookmark an article")
    }

    @Test
    fun an_unreachable_remote_reports_offline_and_leaves_the_cache_intact() = runTest {
        val local = InMemoryArticleLocalStore(listOf(record("cached")))
        val repository = DefaultFeedRepository(
            local,
            FeedRemoteDataSource(
                client = createHttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }),
                config = NetworkConfig(baseUrl = "https://example.test"),
            ),
        )

        val result = repository.refresh()

        assertEquals(FeedRefreshResult.Offline, result)
        assertEquals(listOf("cached"), repository.observeFeed().first().map { it.id.value })
    }

    @Test
    fun a_malformed_response_is_reported_as_a_domain_failure_not_a_parser_error() = runTest {
        val local = InMemoryArticleLocalStore(listOf(record("cached")))
        val repository = DefaultFeedRepository(local, remoteReturning("""{"unexpected":"shape"}"""))

        val result = repository.refresh()

        assertEquals(FeedRefreshResult.Failed(FeedFailure.MalformedResponse), result)
        assertEquals(listOf("cached"), repository.observeFeed().first().map { it.id.value })
    }

    @Test
    fun records_map_to_domain_articles_without_leaking_storage_types() = runTest {
        val local = InMemoryArticleLocalStore(listOf(record("a", title = "Mapped")))
        val repository = DefaultFeedRepository(local, remoteReturning("[]"))

        val article = repository.observeFeed().first().single()

        assertEquals("a", article.id.value)
        assertEquals("Mapped", article.title)
        assertEquals(1_000, article.publishedAtEpochMillis)
    }
}
