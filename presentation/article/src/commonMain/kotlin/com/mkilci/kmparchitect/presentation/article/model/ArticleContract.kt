package com.mkilci.kmparchitect.presentation.article.model

import com.mkilci.kmparchitect.core.mvi.ScreenEvent
import com.mkilci.kmparchitect.core.mvi.ScreenState

data class ArticleDetailUi(
    val title: String,
    val summary: String,
    val sourceLabel: String,
    val url: String,
)

enum class ShareOutcome {
    Shared,
    Cancelled,
    Unavailable,
}

data class ArticleState(
    val article: ArticleDetailUi? = null,
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val isBookmarked: Boolean = false,
    val shareOutcome: ShareOutcome? = null,
) : ScreenState

sealed interface ArticleAction {
    data object ShareClicked : ArticleAction
    data object BookmarkToggled : ArticleAction
    data object BackClicked : ArticleAction
    data object ShareOutcomeDismissed : ArticleAction
}

sealed interface ArticleEvent : ScreenEvent<ArticleState> {

    data class Loaded(val article: ArticleDetailUi) : ArticleEvent {
        override fun reduce(oldState: ArticleState) =
            oldState.copy(article = article, isLoading = false, isMissing = false)
    }

    data object NotFound : ArticleEvent {
        override fun reduce(oldState: ArticleState) =
            oldState.copy(article = null, isLoading = false, isMissing = true)
    }

    data class BookmarkStateChanged(val isBookmarked: Boolean) : ArticleEvent {
        override fun reduce(oldState: ArticleState) = oldState.copy(isBookmarked = isBookmarked)
    }

    data class ShareFinished(val outcome: ShareOutcome) : ArticleEvent {
        override fun reduce(oldState: ArticleState) = oldState.copy(shareOutcome = outcome)
    }

    data object ShareOutcomeDismissed : ArticleEvent {
        override fun reduce(oldState: ArticleState) = oldState.copy(shareOutcome = null)
    }
}
