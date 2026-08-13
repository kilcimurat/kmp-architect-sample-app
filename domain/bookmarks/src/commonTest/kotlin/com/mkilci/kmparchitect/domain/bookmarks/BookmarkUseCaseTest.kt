package com.mkilci.kmparchitect.domain.bookmarks

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun article(id: String, publishedAt: Long) = Article(
    id = ArticleId(id),
    title = "Title $id",
    summary = "Summary $id",
    source = "Source",
    url = "https://example.test/$id",
    publishedAtEpochMillis = publishedAt,
)

private class StubBookmarkRepository(initial: List<Article>) : BookmarkRepository {
    private val stored = MutableStateFlow(initial)
    val removed = mutableListOf<ArticleId>()

    override fun observeBookmarks(): Flow<List<Article>> = stored

    override suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean) {
        if (!bookmarked) {
            removed += id
            stored.update { all -> all.filterNot { it.id == id } }
        }
    }
}

class ObserveBookmarksTest {

    @Test
    fun bookmarks_are_ordered_newest_first() = runTest {
        val repository = StubBookmarkRepository(
            listOf(article("old", 1_000), article("new", 3_000), article("mid", 2_000)),
        )

        val result = ObserveBookmarks(repository).invoke().first()

        assertEquals(listOf("new", "mid", "old"), result.map { it.id.value })
    }

    @Test
    fun an_empty_reading_list_is_a_valid_state() = runTest {
        assertEquals(emptyList(), ObserveBookmarks(StubBookmarkRepository(emptyList())).invoke().first())
    }
}

class RemoveBookmarkTest {

    @Test
    fun removing_clears_the_flag_for_that_article_only() = runTest {
        val repository = StubBookmarkRepository(listOf(article("a", 1), article("b", 2)))

        RemoveBookmark(repository).invoke(ArticleId("a"))

        assertEquals(listOf(ArticleId("a")), repository.removed)
        assertEquals(listOf("b"), repository.observeBookmarks().first().map { it.id.value })
    }
}
