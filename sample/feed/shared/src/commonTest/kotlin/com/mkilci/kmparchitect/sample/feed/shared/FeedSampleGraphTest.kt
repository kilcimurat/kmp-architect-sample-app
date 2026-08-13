package com.mkilci.kmparchitect.sample.feed.shared

import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import com.mkilci.kmparchitect.domain.feed.ObserveFeed
import com.mkilci.kmparchitect.domain.feed.RefreshFeed
import com.mkilci.kmparchitect.fixtures.feed.FeedFixtures
import com.mkilci.kmparchitect.presentation.feed.viewmodel.FeedViewModel
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
import kotlin.test.assertTrue

/**
 * A runtime DI container trades compile-time wiring errors for startup errors. This test is the
 * price of that trade: it proves the sample's graph actually resolves and produces the fixture
 * data the screen will show.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSampleGraphTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        resetFeedSampleKoin()
        startFeedSampleKoinIfNeeded()
    }

    @AfterTest
    fun tearDown() {
        resetFeedSampleKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun the_sample_graph_resolves_the_feature_use_cases() {
        val koin = feedSampleKoin()

        koin.get<ObserveFeed>()
        koin.get<RefreshFeed>()
        koin.get<FeedViewModel>()
    }

    @Test
    fun the_resolved_graph_serves_deterministic_fixture_data_newest_first() = runTest {
        val observeFeed = feedSampleKoin().get<ObserveFeed>()

        val articles = observeFeed().first()

        assertEquals(FeedFixtures.articles.size, articles.size)
        assertEquals(FeedFixtures.architectureArticle.id, articles.first().id)
        assertTrue(articles.zipWithNext().all { (a, b) -> a.publishedAtEpochMillis >= b.publishedAtEpochMillis })
    }

    @Test
    fun the_scripted_refresh_sequence_walks_every_notice_branch() = runTest {
        val refreshFeed = feedSampleKoin().get<RefreshFeed>()

        assertEquals(FeedRefreshResult.Refreshed, refreshFeed())
        assertEquals(FeedRefreshResult.Offline, refreshFeed())
        assertTrue(refreshFeed() is FeedRefreshResult.Failed)
    }
}
