package com.mkilci.kmparchitect.presentation.bookmarks.model

import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.ScreenEvent
import com.mkilci.kmparchitect.core.mvi.ScreenState

data class BookmarkUi(
    val id: ArticleId,
    val title: String,
    val sourceLabel: String,
)

data class BookmarksState(
    val bookmarks: List<BookmarkUi> = emptyList(),
    val isLoading: Boolean = true,
) : ScreenState

sealed interface BookmarksAction {
    data class BookmarkClicked(val id: ArticleId) : BookmarksAction
    data class RemoveClicked(val id: ArticleId) : BookmarksAction
}

sealed interface BookmarksEvent : ScreenEvent<BookmarksState> {
    data class Loaded(val bookmarks: List<BookmarkUi>) : BookmarksEvent {
        override fun reduce(oldState: BookmarksState) =
            oldState.copy(bookmarks = bookmarks, isLoading = false)
    }
}
