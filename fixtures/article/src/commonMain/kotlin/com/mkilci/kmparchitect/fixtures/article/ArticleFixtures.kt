package com.mkilci.kmparchitect.fixtures.article

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import com.mkilci.kmparchitect.domain.article.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * This feature's own fixtures. It deliberately does not reuse `fixtures:feed`: that would be a
 * cross-feature dependency, and the isolation of both samples would depend on the other feature's
 * test data staying still.
 */
object ArticleFixtures {

    val known = Article(
        id = ArticleId("article-architecture"),
        title = "Module-level build isolation in KMP",
        summary = "Why a feature should build without the app it belongs to.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/architecture",
        publishedAtEpochMillis = 1_767_222_000_000,
    )

    val second = Article(
        id = ArticleId("article-effects"),
        title = "Typed one-shot effects",
        summary = "Navigation commands are events, not state.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/effects",
        publishedAtEpochMillis = 1_767_218_400_000,
    )

    val all: List<Article> = listOf(known, second)
}

class FakeArticleRepository(
    articles: List<Article> = ArticleFixtures.all,
    bookmarked: Set<ArticleId> = emptySet(),
) : ArticleRepository {

    private val stored = MutableStateFlow(articles)
    private val bookmarkedIds = MutableStateFlow(bookmarked)

    override fun observeArticle(id: ArticleId): Flow<Article?> =
        stored.map { articles -> articles.find { it.id == id } }

    override fun observeBookmarkState(id: ArticleId): Flow<Boolean> =
        bookmarkedIds.map { ids -> id in ids }

    override suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean) {
        bookmarkedIds.value = if (bookmarked) bookmarkedIds.value + id else bookmarkedIds.value - id
    }
}

/**
 * Records what would have been shared and returns a fixed outcome. A sample must never open a real
 * share sheet by accident, and a test needs to assert on the request rather than on a system UI.
 */
class RecordingSharer(
    private val result: ShareResult = ShareResult.Shared,
) : Sharer {
    val requests: MutableList<ShareRequest> = mutableListOf()

    override suspend fun share(request: ShareRequest): ShareResult {
        requests += request
        return result
    }
}
