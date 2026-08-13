package com.mkilci.kmparchitect.app.shared.di

import com.mkilci.kmparchitect.core.common.AppDispatchers
import com.mkilci.kmparchitect.core.common.DefaultAppDispatchers
import com.mkilci.kmparchitect.core.common.SystemTimeProvider
import com.mkilci.kmparchitect.core.common.TimeProvider
import com.mkilci.kmparchitect.core.network.NetworkConfig
import com.mkilci.kmparchitect.core.network.createHttpClient
import com.mkilci.kmparchitect.data.article.di.articleDataModule
import com.mkilci.kmparchitect.data.articlestore.ArticleLocalStore
import com.mkilci.kmparchitect.data.articlestore.SqlDelightArticleLocalStore
import com.mkilci.kmparchitect.data.bookmarks.di.bookmarksDataModule
import com.mkilci.kmparchitect.data.feed.di.feedDataModule
import com.mkilci.kmparchitect.domain.article.ObserveArticle
import com.mkilci.kmparchitect.domain.article.ObserveArticleBookmarkState
import com.mkilci.kmparchitect.domain.article.SetArticleBookmarked
import com.mkilci.kmparchitect.domain.article.ShareArticle
import com.mkilci.kmparchitect.domain.bookmarks.ObserveBookmarks
import com.mkilci.kmparchitect.domain.bookmarks.RemoveBookmark
import com.mkilci.kmparchitect.domain.feed.ObserveFeed
import com.mkilci.kmparchitect.domain.feed.RefreshFeed
import com.mkilci.kmparchitect.presentation.article.di.articlePresentationModule
import com.mkilci.kmparchitect.presentation.bookmarks.di.bookmarksPresentationModule
import com.mkilci.kmparchitect.presentation.feed.di.feedPresentationModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Reusable use cases, grouped by capability rather than dumped into one root module.
 *
 * They live here and not in the presentation modules for a concrete reason: the same
 * `ObserveFeed(get())` is constructed against a real repository in production and a fixture in the
 * feed sample. A presentation module that constructed it would have to know which.
 */
private val feedUseCaseModule: Module = module {
    factory { ObserveFeed(get()) }
    factory { RefreshFeed(get()) }
}

private val articleUseCaseModule: Module = module {
    factory { ObserveArticle(get()) }
    factory { ObserveArticleBookmarkState(get()) }
    factory { SetArticleBookmarked(get()) }
    factory { ShareArticle(get()) }
}

private val bookmarksUseCaseModule: Module = module {
    factory { ObserveBookmarks(get()) }
    factory { RemoveBookmark(get()) }
}

/** Infrastructure this root selects. The database driver comes from the host; see platformModule. */
private val infrastructureModule: Module = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
    single<TimeProvider> { SystemTimeProvider() }
    single { NetworkConfig(baseUrl = DEMO_BASE_URL) }
    single { createHttpClient(demoBackendEngine()) }
    single<ArticleLocalStore> {
        SqlDelightArticleLocalStore(
            driverFactory = get(),
            dispatcher = get<AppDispatchers>().io,
        )
    }
}

/**
 * Everything the application needs except platform runtime objects.
 *
 * A composition root is allowed to select broadly — assembly is its job. What matters is that each
 * definition still lives next to what it implements, so this list reads as a set of choices rather
 * than as a service locator.
 */
fun sharedProductionModules(): List<Module> = listOf(
    infrastructureModule,
    feedUseCaseModule,
    articleUseCaseModule,
    bookmarksUseCaseModule,
    feedDataModule,
    articleDataModule,
    bookmarksDataModule,
    feedPresentationModule,
    articlePresentationModule,
    bookmarksPresentationModule,
)
