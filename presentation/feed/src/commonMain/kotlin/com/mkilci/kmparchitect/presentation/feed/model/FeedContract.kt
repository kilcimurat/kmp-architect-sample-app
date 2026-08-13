package com.mkilci.kmparchitect.presentation.feed.model

import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.ScreenEvent
import com.mkilci.kmparchitect.core.mvi.ScreenState

/** Render-only model. The screen never sees a domain `Article`, only what it draws. */
data class FeedArticleUi(
    val id: ArticleId,
    val title: String,
    val summary: String,
    val sourceLabel: String,
)

enum class FeedNotice {
    Offline,
    RefreshFailed,
}

data class FeedState(
    val articles: List<FeedArticleUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val notice: FeedNotice? = null,
) : ScreenState

/** UI intent. Never a state change and never a navigation command. */
sealed interface FeedAction {
    data class ArticleClicked(val id: ArticleId) : FeedAction
    data object RefreshClicked : FeedAction
    data object NoticeDismissed : FeedAction
}

/**
 * State transitions. Every `reduce` below is pure and total: no coroutines, no clock, no
 * repository, and no mutation of a collection owned by [oldState].
 */
sealed interface FeedEvent : ScreenEvent<FeedState> {

    data class Loaded(val articles: List<FeedArticleUi>) : FeedEvent {
        override fun reduce(oldState: FeedState) =
            oldState.copy(articles = articles, isLoading = false)
    }

    data object RefreshStarted : FeedEvent {
        override fun reduce(oldState: FeedState) =
            oldState.copy(isRefreshing = true, notice = null)
    }

    data class RefreshFinished(val notice: FeedNotice?) : FeedEvent {
        override fun reduce(oldState: FeedState) =
            oldState.copy(isRefreshing = false, notice = notice)
    }

    data object NoticeDismissed : FeedEvent {
        override fun reduce(oldState: FeedState) = oldState.copy(notice = null)
    }
}
