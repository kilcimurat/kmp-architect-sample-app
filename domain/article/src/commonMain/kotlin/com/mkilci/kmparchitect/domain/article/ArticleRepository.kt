package com.mkilci.kmparchitect.domain.article

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import kotlinx.coroutines.flow.Flow

/**
 * Reading one article. A `null` emission means "not in local storage", which is an ordinary state
 * for an offline-first app rather than an error.
 */
interface ArticleRepository {
    fun observeArticle(id: ArticleId): Flow<Article?>

    /**
     * Whether this article is on the reading list.
     *
     * The article feature owns *setting* the flag because that is an action on the article screen;
     * the bookmarks feature owns *listing* what is flagged. They agree through shared storage, not
     * through a dependency on each other — which is what keeps either one buildable alone.
     */
    fun observeBookmarkState(id: ArticleId): Flow<Boolean>

    suspend fun setBookmarked(id: ArticleId, bookmarked: Boolean)
}

class ObserveArticle(
    private val repository: ArticleRepository,
) {
    operator fun invoke(id: ArticleId): Flow<Article?> = repository.observeArticle(id)
}

class ObserveArticleBookmarkState(
    private val repository: ArticleRepository,
) {
    operator fun invoke(id: ArticleId): Flow<Boolean> = repository.observeBookmarkState(id)
}

class SetArticleBookmarked(
    private val repository: ArticleRepository,
) {
    suspend operator fun invoke(id: ArticleId, bookmarked: Boolean) =
        repository.setBookmarked(id, bookmarked)
}

/**
 * Sharing is business behaviour — what gets shared and in what shape — so it lives here, while
 * *how* the sheet is presented stays behind the [Sharer] port that hosts implement. This use case
 * has no idea whether it is talking to an Android chooser, a UIActivityViewController, or a fake.
 */
class ShareArticle(
    private val sharer: Sharer,
) {
    suspend operator fun invoke(article: Article): ShareResult = sharer.share(
        ShareRequest(
            title = article.title,
            url = article.url,
        ),
    )
}
