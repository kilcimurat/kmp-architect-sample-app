package com.mkilci.kmparchitect.presentation.article.viewmodel

import androidx.lifecycle.viewModelScope
import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.MviViewModel
import com.mkilci.kmparchitect.core.mvi.StateStore
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.domain.article.ObserveArticle
import com.mkilci.kmparchitect.domain.article.ObserveArticleBookmarkState
import com.mkilci.kmparchitect.domain.article.SetArticleBookmarked
import com.mkilci.kmparchitect.domain.article.ShareArticle
import com.mkilci.kmparchitect.presentation.article.model.ArticleAction
import com.mkilci.kmparchitect.presentation.article.model.ArticleDetailUi
import com.mkilci.kmparchitect.presentation.article.model.ArticleEvent
import com.mkilci.kmparchitect.presentation.article.model.ArticleState
import com.mkilci.kmparchitect.presentation.article.model.ShareOutcome
import com.mkilci.kmparchitect.presentation.article.navigation.ArticleEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ArticleViewModel(
    stateStore: StateStore<ArticleState, ArticleEvent>,
    private val articleId: ArticleId,
    private val observeArticle: ObserveArticle,
    private val observeBookmarkState: ObserveArticleBookmarkState,
    private val setArticleBookmarked: SetArticleBookmarked,
    private val shareArticle: ShareArticle,
) : MviViewModel<ArticleState, ArticleEvent, ArticleEffect>(stateStore) {

    // Kept so sharing does not have to re-read storage; it is a cache of the last loaded article,
    // never a second source of truth for the screen.
    private val loaded = MutableStateFlow<Article?>(null)

    init {
        viewModelScope.launch {
            observeArticle(articleId).collect { article ->
                loaded.value = article
                sendEvent(
                    if (article == null) ArticleEvent.NotFound else ArticleEvent.Loaded(article.toUi()),
                )
            }
        }
        viewModelScope.launch {
            observeBookmarkState(articleId).collect { isBookmarked ->
                sendEvent(ArticleEvent.BookmarkStateChanged(isBookmarked))
            }
        }
    }

    fun onAction(action: ArticleAction) {
        when (action) {
            // Synchronous effect, straight from a ViewModel-owned method: no coroutine needed.
            ArticleAction.BackClicked -> sendEffect(ArticleEffect.NavigateBack)
            ArticleAction.ShareClicked -> share()
            ArticleAction.BookmarkToggled -> toggleBookmark()
            ArticleAction.ShareOutcomeDismissed -> sendEvent(ArticleEvent.ShareOutcomeDismissed)
        }
    }

    // No optimistic state write: the flag is stored, and the observed bookmark state is what
    // updates the screen. One source of truth, exactly as with the article itself.
    private fun toggleBookmark() {
        val target = !state.value.isBookmarked
        viewModelScope.launch { setArticleBookmarked(articleId, target) }
    }

    private fun share() {
        val article = loaded.value ?: return
        viewModelScope.launch {
            val outcome = when (shareArticle(article)) {
                ShareResult.Shared -> ShareOutcome.Shared
                ShareResult.Cancelled -> ShareOutcome.Cancelled
                ShareResult.Unavailable -> ShareOutcome.Unavailable
            }
            sendEvent(ArticleEvent.ShareFinished(outcome))
        }
    }
}

private fun Article.toUi() = ArticleDetailUi(
    title = title,
    summary = summary,
    sourceLabel = source,
    url = url,
)
