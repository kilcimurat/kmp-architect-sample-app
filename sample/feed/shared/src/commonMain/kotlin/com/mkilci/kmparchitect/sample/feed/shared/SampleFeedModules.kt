package com.mkilci.kmparchitect.sample.feed.shared

import com.mkilci.kmparchitect.domain.feed.FeedRefreshResult
import com.mkilci.kmparchitect.domain.feed.FeedRepository
import com.mkilci.kmparchitect.domain.feed.ObserveFeed
import com.mkilci.kmparchitect.domain.feed.RefreshFeed
import com.mkilci.kmparchitect.fixtures.feed.FakeFeedRepository
import com.mkilci.kmparchitect.fixtures.feed.FeedFixtures
import com.mkilci.kmparchitect.presentation.feed.di.feedPresentationModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * The sample's composition root.
 *
 * It selects exactly three things: the feature's presentation module, the reusable use cases that
 * feature needs, and deterministic fixtures for its ports. No `data`, no app root, no other
 * feature — which is what makes `:sample:feed:androidApp:installDebug` a small build.
 *
 * The refresh sequence is scripted rather than random so the sample demonstrates every branch of
 * the notice banner in a fixed order: success, then offline, then failure.
 */
fun sampleFeedModules(): List<Module> = listOf(
    module {
        single<FeedRepository> {
            FakeFeedRepository(
                initialArticles = FeedFixtures.articles,
                refreshResults = listOf(
                    FeedRefreshResult.Refreshed,
                    FeedRefreshResult.Offline,
                    FeedRefreshResult.Failed(com.mkilci.kmparchitect.domain.feed.FeedFailure.RemoteUnavailable),
                ),
            )
        }
        factory { ObserveFeed(get()) }
        factory { RefreshFeed(get()) }
    },
    feedPresentationModule,
)

private var started = false

/** Idempotent so a native host can call it from more than one entry point. */
fun startFeedSampleKoinIfNeeded() {
    if (started) return
    started = true
    startKoin { modules(sampleFeedModules()) }
}

/** Test-only reset; keeps repeated graph-startup tests independent. */
fun resetFeedSampleKoin() {
    if (!started) return
    stopKoin()
    started = false
}

internal fun feedSampleKoin() = KoinPlatform.getKoin()
