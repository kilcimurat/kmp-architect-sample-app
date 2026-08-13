package com.mkilci.kmparchitect.presentation.bookmarks

import com.mkilci.kmparchitect.core.mvi.DefaultStateStore
import com.mkilci.kmparchitect.domain.bookmarks.ObserveBookmarks
import com.mkilci.kmparchitect.domain.bookmarks.RemoveBookmark
import com.mkilci.kmparchitect.fixtures.bookmarks.BookmarkFixtures
import com.mkilci.kmparchitect.fixtures.bookmarks.FakeBookmarkRepository
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksAction
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksState
import com.mkilci.kmparchitect.presentation.bookmarks.navigation.BookmarksEffect
import com.mkilci.kmparchitect.presentation.bookmarks.viewmodel.BookmarksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarksViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeBookmarkRepository = FakeBookmarkRepository()) =
        BookmarksViewModel(
            stateStore = DefaultStateStore(BookmarksState()),
            observeBookmarks = ObserveBookmarks(repository),
            removeBookmark = RemoveBookmark(repository),
        )

    @Test
    fun saved_articles_reach_state_newest_first() = runTest {
        val state = viewModel().state.value

        assertEquals(false, state.isLoading)
        assertEquals(
            listOf(BookmarkFixtures.effects.title, BookmarkFixtures.benchmarks.title),
            state.bookmarks.map { it.title },
        )
    }

    @Test
    fun opening_a_bookmark_emits_a_typed_effect_this_feature_cannot_handle_itself() = runTest {
        val vm = viewModel()

        vm.onAction(BookmarksAction.BookmarkClicked(BookmarkFixtures.effects.id))

        // The effect names the request only. Bookmarks does not depend on the article feature, so
        // the graph owner decides where it goes -- production to the article screen, the sample to
        // a placeholder.
        assertEquals(
            BookmarksEffect.OpenArticle(BookmarkFixtures.effects.id),
            withTimeout(1_000) { vm.effects.first() },
        )
    }

    @Test
    fun removing_a_bookmark_updates_state_through_the_repository() = runTest {
        val vm = viewModel()
        assertEquals(2, vm.state.value.bookmarks.size)

        vm.onAction(BookmarksAction.RemoveClicked(BookmarkFixtures.effects.id))

        val remaining = vm.state.value.bookmarks
        assertEquals(1, remaining.size)
        assertTrue(remaining.none { it.id == BookmarkFixtures.effects.id })
    }

    @Test
    fun an_empty_reading_list_stops_loading_rather_than_hanging() = runTest {
        val vm = viewModel(FakeBookmarkRepository(initial = emptyList()))

        assertEquals(false, vm.state.value.isLoading)
        assertTrue(vm.state.value.bookmarks.isEmpty())
    }
}
