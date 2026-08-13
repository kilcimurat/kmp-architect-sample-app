package com.mkilci.kmparchitect.data.bookmarks

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.data.articlestore.ArticleLocalStore
import com.mkilci.kmparchitect.data.articlestore.ArticleRecord
import com.mkilci.kmparchitect.domain.bookmarks.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bookmarks are a flag on the shared article store, not a second copy of the article. That is what
 * lets a feed refresh update a bookmarked article's content without the reading list going stale.
 */
class DefaultBookmarkRepository(
    private val local: ArticleLocalStore,
) : BookmarkRepository {

    override fun observeBookmarks(): Flow<List<Article>> =
        local.observeBookmarked().map { records -> records.map(ArticleRecord::toDomain) }

    override suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean) =
        local.setBookmarked(id.value, bookmarked)
}

internal fun ArticleRecord.toDomain() = Article(
    id = ArticleId(id),
    title = title,
    summary = summary,
    source = source,
    url = url,
    publishedAtEpochMillis = publishedAtEpochMillis,
)
