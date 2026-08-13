package com.mkilci.kmparchitect.domain.article

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val sample = Article(
    id = ArticleId("a"),
    title = "A title",
    summary = "A summary",
    source = "Source",
    url = "https://example.test/a",
    publishedAtEpochMillis = 1_000,
)

private class StubArticleRepository(
    private val article: Article?,
    initiallyBookmarked: Boolean = false,
) : ArticleRepository {

    private val bookmarked = MutableStateFlow(initiallyBookmarked)

    override fun observeArticle(id: ArticleId): Flow<Article?> = flowOf(article)
    override fun observeBookmarkState(id: ArticleId): Flow<Boolean> = bookmarked
    override suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean) {
        this.bookmarked.value = bookmarked
    }
}

private class StubSharer(private val result: ShareResult) : Sharer {
    var lastRequest: ShareRequest? = null
    override suspend fun share(request: ShareRequest): ShareResult {
        lastRequest = request
        return result
    }
}

class ObserveArticleTest {

    @Test
    fun a_known_article_is_emitted() = runTest {
        assertEquals(sample, ObserveArticle(StubArticleRepository(sample)).invoke(ArticleId("a")).first())
    }

    @Test
    fun a_missing_article_emits_null_rather_than_failing() = runTest {
        assertNull(ObserveArticle(StubArticleRepository(null)).invoke(ArticleId("missing")).first())
    }
}

class ShareArticleTest {

    @Test
    fun the_share_request_carries_the_articles_title_and_url() = runTest {
        val sharer = StubSharer(ShareResult.Shared)

        val result = ShareArticle(sharer).invoke(sample)

        assertEquals(ShareResult.Shared, result)
        assertEquals(ShareRequest(title = "A title", url = "https://example.test/a"), sharer.lastRequest)
    }

    @Test
    fun an_unavailable_share_surface_is_a_normal_result_not_an_exception() = runTest {
        val result = ShareArticle(StubSharer(ShareResult.Unavailable)).invoke(sample)

        assertEquals(ShareResult.Unavailable, result)
    }
}
