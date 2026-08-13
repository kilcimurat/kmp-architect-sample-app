package com.mkilci.kmparchitect.data.feed

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.data.articlestore.ArticleLocalStore
import com.mkilci.kmparchitect.data.articlestore.ArticleRecord
import com.mkilci.kmparchitect.domain.feed.FeedFailure
import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import com.mkilci.kmparchitect.domain.feed.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first, in the strict sense: [observeFeed] reads local storage and only local storage.
 * [refresh] writes the remote response *into* that store and returns what happened. The UI never
 * receives a second list to reconcile, so there is no window in which two sources disagree.
 *
 * A failed refresh therefore leaves the cached feed intact and reports a reason. That is why
 * `FeedRefreshResult` distinguishes `Offline` from `Failed`: one is expected and temporary, the
 * other is worth telling the user about.
 */
class DefaultFeedRepository(
    private val local: ArticleLocalStore,
    private val remote: FeedRemoteDataSource,
) : FeedRepository {

    override fun observeFeed(): Flow<List<Article>> =
        local.observeAll().map { records -> records.map(ArticleRecord::toDomain) }

    override suspend fun refresh(): FeedRefreshResult = when (val result = remote.fetchFeed()) {
        is RemoteFeedResult.Available -> {
            local.upsertAll(result.records)
            FeedRefreshResult.Refreshed
        }
        RemoteFeedResult.Unreachable -> FeedRefreshResult.Offline
        RemoteFeedResult.Malformed -> FeedRefreshResult.Failed(FeedFailure.MalformedResponse)
    }
}

internal fun ArticleRecord.toDomain() = Article(
    id = ArticleId(id),
    title = title,
    summary = summary,
    source = source,
    url = url,
    publishedAtEpochMillis = publishedAtEpochMillis,
)
