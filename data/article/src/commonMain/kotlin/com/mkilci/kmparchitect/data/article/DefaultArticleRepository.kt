package com.mkilci.kmparchitect.data.article

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.data.articlestore.ArticleLocalStore
import com.mkilci.kmparchitect.data.articlestore.ArticleRecord
import com.mkilci.kmparchitect.domain.article.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads the same local store the feed writes. The features stay independent; their *storage* is
 * shared, which is what an offline-first app with one entity actually looks like.
 *
 * The record-to-domain mapping is repeated in each feature's data module rather than hoisted into
 * the store. That is deliberate: mapping is owned by the module that decides what the feature needs,
 * and hoisting it would make the shared store speak domain types to everyone.
 */
class DefaultArticleRepository(
    private val local: ArticleLocalStore,
) : ArticleRepository {

    override fun observeArticle(id: ArticleId): Flow<Article?> =
        local.observeById(id.value).map { record -> record?.toDomain() }

    override fun observeBookmarkState(id: ArticleId): Flow<Boolean> =
        local.observeById(id.value).map { record -> record?.bookmarked == true }

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
