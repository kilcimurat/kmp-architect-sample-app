package com.mkilci.kmparchitect.sample.bookmarks.shared

import com.mkilci.kmparchitect.domain.bookmarks.BookmarkRepository
import com.mkilci.kmparchitect.domain.bookmarks.ObserveBookmarks
import com.mkilci.kmparchitect.domain.bookmarks.RemoveBookmark
import com.mkilci.kmparchitect.fixtures.bookmarks.FakeBookmarkRepository
import com.mkilci.kmparchitect.presentation.bookmarks.di.bookmarksPresentationModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

fun sampleBookmarksModules(): List<Module> = listOf(
    module {
        single<BookmarkRepository> { FakeBookmarkRepository() }
        factory { ObserveBookmarks(get()) }
        factory { RemoveBookmark(get()) }
    },
    bookmarksPresentationModule,
)

private var started = false

fun startBookmarksSampleKoinIfNeeded() {
    if (started) return
    started = true
    startKoin { modules(sampleBookmarksModules()) }
}

fun resetBookmarksSampleKoin() {
    if (!started) return
    stopKoin()
    started = false
}

internal fun bookmarksSampleKoin() = KoinPlatform.getKoin()
