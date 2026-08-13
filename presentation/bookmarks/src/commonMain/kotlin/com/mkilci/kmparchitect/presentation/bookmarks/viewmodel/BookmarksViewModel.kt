package com.mkilci.kmparchitect.presentation.bookmarks.viewmodel

import androidx.lifecycle.viewModelScope
import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.MviViewModel
import com.mkilci.kmparchitect.core.mvi.StateStore
import com.mkilci.kmparchitect.domain.bookmarks.ObserveBookmarks
import com.mkilci.kmparchitect.domain.bookmarks.RemoveBookmark
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarkUi
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksAction
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksEvent
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksState
import com.mkilci.kmparchitect.presentation.bookmarks.navigation.BookmarksEffect
import kotlinx.coroutines.launch

class BookmarksViewModel(
    stateStore: StateStore<BookmarksState, BookmarksEvent>,
    private val observeBookmarks: ObserveBookmarks,
    private val removeBookmark: RemoveBookmark,
) : MviViewModel<BookmarksState, BookmarksEvent, BookmarksEffect>(stateStore) {

    init {
        viewModelScope.launch {
            observeBookmarks().collect { articles ->
                sendEvent(BookmarksEvent.Loaded(articles.map(Article::toUi)))
            }
        }
    }

    fun onAction(action: BookmarksAction) {
        when (action) {
            is BookmarksAction.BookmarkClicked -> sendEffect(BookmarksEffect.OpenArticle(action.id))
            is BookmarksAction.RemoveClicked -> remove(action.id)
        }
    }

    private fun remove(id: ArticleId) {
        viewModelScope.launch { removeBookmark(id) }
    }
}

private fun Article.toUi() = BookmarkUi(id = id, title = title, sourceLabel = source)
