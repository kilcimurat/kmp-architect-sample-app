package com.mkilci.kmparchitect.fixtures.feed

import com.mkilci.kmparchitect.core.model.Article
import com.mkilci.kmparchitect.core.model.ArticleId

/**
 * Fixed, human-recognisable data.
 *
 * Recognisable matters: when the sample launches, whoever is looking at it should be able to tell
 * at a glance that the list is the fixture and not a half-loaded real feed. Timestamps are absolute
 * constants so ordering assertions and screenshots never depend on when the test ran.
 */
object FeedFixtures {

    const val FIXED_NOW_EPOCH_MILLIS: Long = 1_767_225_600_000 // 2026-01-01T00:00:00Z

    val architectureArticle = Article(
        id = ArticleId("article-architecture"),
        title = "Module-level build isolation in KMP",
        summary = "Why a feature should build without the app it belongs to.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/architecture",
        publishedAtEpochMillis = FIXED_NOW_EPOCH_MILLIS - 3_600_000,
    )

    val effectsArticle = Article(
        id = ArticleId("article-effects"),
        title = "Typed one-shot effects",
        summary = "Navigation commands are events, not state.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/effects",
        publishedAtEpochMillis = FIXED_NOW_EPOCH_MILLIS - 7_200_000,
    )

    val benchmarksArticle = Article(
        id = ArticleId("article-benchmarks"),
        title = "Measuring what the diagram promised",
        summary = "Isolation claims that survive a stopwatch.",
        source = "Fixture Weekly",
        url = "https://example.test/articles/benchmarks",
        publishedAtEpochMillis = FIXED_NOW_EPOCH_MILLIS - 10_800_000,
    )

    /** Deliberately not in published order, so ordering rules are actually exercised. */
    val articles: List<Article> = listOf(effectsArticle, benchmarksArticle, architectureArticle)
}
