package com.mkilci.kmparchitect.presentation.feed.viewmodel

import androidx.lifecycle.viewModelScope
import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.mvi.MviViewModel
import com.mkilci.kmparchitect.core.mvi.StateStore
import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import com.mkilci.kmparchitect.domain.feed.ObserveFeed
import com.mkilci.kmparchitect.domain.feed.RefreshFeed
import com.mkilci.kmparchitect.presentation.feed.model.FeedAction
import com.mkilci.kmparchitect.presentation.feed.model.FeedArticleUi
import com.mkilci.kmparchitect.presentation.feed.model.FeedEvent
import com.mkilci.kmparchitect.presentation.feed.model.FeedNotice
import com.mkilci.kmparchitect.presentation.feed.model.FeedState
import com.mkilci.kmparchitect.presentation.feed.navigation.FeedEffect
import kotlinx.coroutines.launch

class FeedViewModel(
    stateStore: StateStore<FeedState, FeedEvent>,
    private val observeFeed: ObserveFeed,
    private val refreshFeed: RefreshFeed,
) : MviViewModel<FeedState, FeedEvent, FeedEffect>(stateStore) {

    init {
        viewModelScope.launch {
            observeFeed().collect { articles ->
                sendEvent(FeedEvent.Loaded(articles.map(Article::toUi)))
            }
        }
    }

    fun onAction(action: FeedAction) {
        when (action) {
            is FeedAction.ArticleClicked -> sendEffect(FeedEffect.OpenArticle(action.id))
            FeedAction.RefreshClicked -> refresh()
            FeedAction.NoticeDismissed -> sendEvent(FeedEvent.NoticeDismissed)
        }
    }

    // Orchestration lives here, not in a reducer. The completed result becomes an Event.
    private fun refresh() {
        viewModelScope.launch {
            sendEvent(FeedEvent.RefreshStarted)
            val notice = when (refreshFeed()) {
                FeedRefreshResult.Refreshed -> null
                FeedRefreshResult.Offline -> FeedNotice.Offline
                is FeedRefreshResult.Failed -> FeedNotice.RefreshFailed
            }
            sendEvent(FeedEvent.RefreshFinished(notice))
        }
    }
}

private fun Article.toUi(): FeedArticleUi = FeedArticleUi(
    id = id,
    title = title,
    summary = summary,
    sourceLabel = source,
)
