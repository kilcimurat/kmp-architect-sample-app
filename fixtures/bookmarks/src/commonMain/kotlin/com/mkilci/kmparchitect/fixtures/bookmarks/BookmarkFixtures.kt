package com.mkilci.kmparchitect.fixtures.bookmarks

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.domain.bookmarks.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object BookmarkFixtures {

    val effects = Article(
        id = ArticleId("article-effects"),
        title = "Typed one-shot effects",
        summary = "Navigation commands are events, not state.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/effects",
        publishedAtEpochMillis = 1_767_218_400_000,
    )

    val benchmarks = Article(
        id = ArticleId("article-benchmarks"),
        title = "Measuring what the diagram promised",
        summary = "Isolation claims that survive a stopwatch.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/benchmarks",
        publishedAtEpochMillis = 1_767_214_800_000,
    )

    val saved: List<Article> = listOf(benchmarks, effects)
}

class FakeBookmarkRepository(
    initial: List<Article> = BookmarkFixtures.saved,
) : BookmarkRepository {

    private val stored = MutableStateFlow(initial)

    override fun observeBookmarks(): Flow<List<Article>> = stored

    override suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean) {
        if (!bookmarked) {
            stored.update { all -> all.filterNot { it.id == id } }
        }
    }
}
