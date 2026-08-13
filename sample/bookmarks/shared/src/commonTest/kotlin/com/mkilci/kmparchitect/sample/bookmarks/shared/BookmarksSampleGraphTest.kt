package com.mkilci.kmparchitect.sample.bookmarks.shared

import com.mkilci.kmparchitect.domain.bookmarks.ObserveBookmarks
import com.mkilci.kmparchitect.domain.bookmarks.RemoveBookmark
import com.mkilci.kmparchitect.fixtures.bookmarks.BookmarkFixtures
import com.mkilci.kmparchitect.presentation.bookmarks.viewmodel.BookmarksViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksSampleGraphTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        resetBookmarksSampleKoin()
        startBookmarksSampleKoinIfNeeded()
    }

    @AfterTest
    fun tearDown() {
        resetBookmarksSampleKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun the_sample_graph_resolves_the_feature_use_cases() {
        bookmarksSampleKoin().get<ObserveBookmarks>()
        bookmarksSampleKoin().get<RemoveBookmark>()
        bookmarksSampleKoin().get<BookmarksViewModel>()
    }

    @Test
    fun the_resolved_graph_serves_deterministic_fixture_bookmarks_newest_first() = runTest {
        val bookmarks = bookmarksSampleKoin().get<ObserveBookmarks>().invoke().first()

        assertEquals(
            listOf(BookmarkFixtures.effects.id, BookmarkFixtures.benchmarks.id),
            bookmarks.map { it.id },
        )
    }
}
