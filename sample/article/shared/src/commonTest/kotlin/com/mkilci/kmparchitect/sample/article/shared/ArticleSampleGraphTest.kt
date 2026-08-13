package com.mkilci.kmparchitect.sample.article.shared

import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import com.mkilci.kmparchitect.domain.article.ObserveArticle
import com.mkilci.kmparchitect.domain.article.ShareArticle
import com.mkilci.kmparchitect.fixtures.article.ArticleFixtures
import com.mkilci.kmparchitect.fixtures.article.RecordingSharer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArticleSampleGraphTest {

    @BeforeTest
    fun setUp() {
        resetArticleSampleKoin()
        startArticleSampleKoinIfNeeded()
    }

    @AfterTest
    fun tearDown() = resetArticleSampleKoin()

    @Test
    fun the_sample_graph_resolves_the_feature_use_cases() {
        articleSampleKoin().get<ObserveArticle>()
        articleSampleKoin().get<ShareArticle>()
    }

    @Test
    fun the_resolved_graph_serves_the_deterministic_fixture_article() = runTest {
        val observeArticle = articleSampleKoin().get<ObserveArticle>()

        val article = observeArticle(ArticleFixtures.known.id).first()

        assertEquals(ArticleFixtures.known, article)
    }

    @Test
    fun the_bound_sharer_records_instead_of_opening_a_real_share_sheet() = runTest {
        val sharer = articleSampleKoin().get<Sharer>()
        assertIs<RecordingSharer>(sharer, "a sample must not bind a real share surface")

        val result = sharer.share(ShareRequest(title = "t", url = "u"))

        assertEquals(ShareResult.Shared, result)
        assertTrue(sharer.requests.isNotEmpty())
    }
}
