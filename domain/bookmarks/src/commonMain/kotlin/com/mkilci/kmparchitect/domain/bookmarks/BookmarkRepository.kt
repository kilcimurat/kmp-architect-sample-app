package com.mkilci.kmparchitect.domain.bookmarks

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<Article>>
    suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean)
}

/** Most recently published first — a reading list, not an archive. */
class ObserveBookmarks(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(): Flow<List<Article>> =
        repository.observeBookmarks().map { articles ->
            articles.sortedByDescending(Article::publishedAtEpochMillis)
        }
}

class RemoveBookmark(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(id: ArticleId) = repository.setBookmarked(id, bookmarked = false)
}
