package com.mkilci.kmparchitect.sample.bookmarks.shared

import com.mkilci.kmparchitect.domain.bookmarks.ObserveBookmarks
import com.mkilci.kmparchitect.domain.bookmarks.RemoveBookmark
import com.mkilci.kmparchitect.fixtures.bookmarks.BookmarkFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarksSampleGraphTest {

    @BeforeTest
    fun setUp() {
        resetBookmarksSampleKoin()
        startBookmarksSampleKoinIfNeeded()
    }

    @AfterTest
    fun tearDown() = resetBookmarksSampleKoin()

    @Test
    fun the_sample_graph_resolves_the_feature_use_cases() {
        bookmarksSampleKoin().get<ObserveBookmarks>()
        bookmarksSampleKoin().get<RemoveBookmark>()
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
