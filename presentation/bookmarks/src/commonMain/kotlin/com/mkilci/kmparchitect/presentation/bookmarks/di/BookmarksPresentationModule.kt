package com.mkilci.kmparchitect.presentation.bookmarks.di

import com.mkilci.kmparchitect.core.mvi.DefaultStateStore
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksState
import com.mkilci.kmparchitect.presentation.bookmarks.viewmodel.BookmarksViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookmarksPresentationModule: Module = module {
    viewModel {
        BookmarksViewModel(
            stateStore = DefaultStateStore(BookmarksState()),
            observeBookmarks = get(),
            removeBookmark = get(),
        )
    }
}
