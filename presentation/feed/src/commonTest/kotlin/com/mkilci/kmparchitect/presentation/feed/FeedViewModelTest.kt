package com.mkilci.kmparchitect.presentation.feed

import com.mkilci.kmparchitect.core.mvi.DefaultStateStore
import com.mkilci.kmparchitect.domain.feed.FeedFailure
import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import com.mkilci.kmparchitect.domain.feed.ObserveFeed
import com.mkilci.kmparchitect.domain.feed.RefreshFeed
import com.mkilci.kmparchitect.fixtures.feed.FakeFeedRepository
import com.mkilci.kmparchitect.fixtures.feed.FeedFixtures
import com.mkilci.kmparchitect.presentation.feed.model.FeedAction
import com.mkilci.kmparchitect.presentation.feed.model.FeedEvent
import com.mkilci.kmparchitect.presentation.feed.model.FeedNotice
import com.mkilci.kmparchitect.presentation.feed.model.FeedState
import com.mkilci.kmparchitect.presentation.feed.navigation.FeedEffect
import com.mkilci.kmparchitect.presentation.feed.viewmodel.FeedViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uses `fixtures:feed`, the same fake the sample runs against. There is no second "test version" of
 * the feed repository to drift from it.
 */
class FeedViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeFeedRepository) = FeedViewModel(
        stateStore = DefaultStateStore(FeedState()),
        observeFeed = ObserveFeed(repository),
        refreshFeed = RefreshFeed(repository),
    )

    @Test
    fun repository_output_reaches_state_through_event_reduction() = runTest {
        val viewModel = viewModel(FakeFeedRepository())

        val state = viewModel.state.value

        assertEquals(false, state.isLoading)
        assertEquals(FeedFixtures.articles.size, state.articles.size)
        assertEquals(FeedFixtures.architectureArticle.title, state.articles.first().title)
    }

    @Test
    fun clicking_an_article_emits_a_typed_effect_and_leaves_state_alone() = runTest {
        val viewModel = viewModel(FakeFeedRepository())
        val before = viewModel.state.value

        viewModel.onAction(FeedAction.ArticleClicked(FeedFixtures.effectsArticle.id))

        val effect = withTimeout(1_000) { viewModel.effects.first() }
        assertEquals(FeedEffect.OpenArticle(FeedFixtures.effectsArticle.id), effect)
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun an_offline_refresh_becomes_a_notice_rather_than_an_error_screen() = runTest {
        val repository = FakeFeedRepository(refreshResults = listOf(FeedRefreshResult.Offline))
        val viewModel = viewModel(repository)

        viewModel.onAction(FeedAction.RefreshClicked)

        val state = viewModel.state.value
        assertEquals(FeedNotice.Offline, state.notice)
        assertEquals(false, state.isRefreshing)
        assertTrue(state.articles.isNotEmpty(), "cached articles must survive a failed refresh")
    }

    @Test
    fun a_failed_refresh_reports_a_domain_reason() = runTest {
        val repository = FakeFeedRepository(
            refreshResults = listOf(FeedRefreshResult.Failed(FeedFailure.RemoteUnavailable)),
        )
        val viewModel = viewModel(repository)

        viewModel.onAction(FeedAction.RefreshClicked)

        assertEquals(FeedNotice.RefreshFailed, viewModel.state.value.notice)
    }

    @Test
    fun a_successful_refresh_publishes_new_articles_and_clears_the_notice() = runTest {
        val repository = FakeFeedRepository(
            initialArticles = listOf(FeedFixtures.architectureArticle),
            refreshResults = listOf(FeedRefreshResult.Refreshed),
            articlesAddedOnRefresh = listOf(FeedFixtures.effectsArticle),
        )
        val viewModel = viewModel(repository)
        assertEquals(1, viewModel.state.value.articles.size)

        viewModel.onAction(FeedAction.RefreshClicked)

        assertEquals(2, viewModel.state.value.articles.size)
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun dismissing_a_notice_is_a_state_change_not_an_effect() = runTest {
        val repository = FakeFeedRepository(refreshResults = listOf(FeedRefreshResult.Offline))
        val viewModel = viewModel(repository)
        viewModel.onAction(FeedAction.RefreshClicked)

        viewModel.onAction(FeedAction.NoticeDismissed)

        assertNull(viewModel.state.value.notice)
    }
}

class FeedReducerTest {

    @Test
    fun refresh_started_clears_a_previous_notice_without_touching_the_article_list() {
        val articles = FeedState().articles
        val oldState = FeedState(isLoading = false, notice = FeedNotice.Offline, articles = articles)

        val newState = FeedEvent.RefreshStarted.reduce(oldState)

        assertTrue(newState.isRefreshing)
        assertNull(newState.notice)
        assertEquals(oldState.articles, newState.articles)
        assertEquals(FeedNotice.Offline, oldState.notice, "the previous state was mutated")
    }

    @Test
    fun reducing_the_same_event_twice_is_deterministic() {
        val oldState = FeedState(isLoading = true)
        val event = FeedEvent.RefreshFinished(FeedNotice.RefreshFailed)

        assertEquals(event.reduce(oldState), event.reduce(oldState))
    }
}
