package com.mkilci.kmparchitect.presentation.feed.di

import com.mkilci.kmparchitect.core.mvi.DefaultStateStore
import com.mkilci.kmparchitect.presentation.feed.model.FeedState
import com.mkilci.kmparchitect.presentation.feed.viewmodel.FeedViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * This module defines exactly one thing: the ViewModel this feature owns.
 *
 * `ObserveFeed` and `RefreshFeed` are deliberately absent. They are reusable application operations,
 * so the active composition root constructs them — production against the real repository, the
 * sample against a fixture. If they were defined here, this module would carry an opinion about
 * which repository is live.
 */
val feedPresentationModule: Module = module {
    viewModel {
        FeedViewModel(
            stateStore = DefaultStateStore(FeedState()),
            observeFeed = get(),
            refreshFeed = get(),
        )
    }
}
