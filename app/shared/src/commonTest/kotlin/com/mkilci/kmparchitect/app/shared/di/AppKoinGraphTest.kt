package com.mkilci.kmparchitect.app.shared.di

import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import com.mkilci.kmparchitect.data.articlestore.ArticleLocalStore
import com.mkilci.kmparchitect.data.articlestore.ArticleRecord
import com.mkilci.kmparchitect.presentation.article.viewmodel.ArticleViewModel
import com.mkilci.kmparchitect.presentation.bookmarks.viewmodel.BookmarksViewModel
import com.mkilci.kmparchitect.presentation.feed.viewmodel.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val graphArticle = ArticleRecord(
    id = "graph-article",
    title = "Production graph article",
    summary = "Resolved through the production composition root.",
    source = "Graph test",
    url = "https://example.test/graph",
    publishedAtEpochMillis = 1_000,
    bookmarked = true,
)

private class InMemoryArticleLocalStore : ArticleLocalStore {
    private val records = MutableStateFlow(listOf(graphArticle))

    override fun observeAll(): Flow<List<ArticleRecord>> = records
    override fun observeById(id: String): Flow<ArticleRecord?> = records.map { all -> all.find { it.id == id } }
    override fun observeBookmarked(): Flow<List<ArticleRecord>> = records.map { all -> all.filter { it.bookmarked } }

    override suspend fun upsertAll(records: List<ArticleRecord>) {
        this.records.value = records
    }

    override suspend fun setBookmarked(id: String, bookmarked: Boolean) {
        records.update { all -> all.map { if (it.id == id) it.copy(bookmarked = bookmarked) else it } }
    }
}

private object RecordingTestSharer : Sharer {
    override suspend fun share(request: ShareRequest): ShareResult = ShareResult.Shared
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppKoinGraphTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun production_graph_starts_and_resolves_every_initial_feature_viewmodel() = runTest {
        val application = koinApplication {
            allowOverride(true)
            modules(
                sharedProductionModules() + module {
                    single<ArticleLocalStore> { InMemoryArticleLocalStore() }
                    single<Sharer> { RecordingTestSharer }
                },
            )
        }

        try {
            val feed = application.koin.get<FeedViewModel>()
            val bookmarks = application.koin.get<BookmarksViewModel>()
            val article = application.koin.get<ArticleViewModel> { parametersOf(graphArticle.id) }

            assertEquals(graphArticle.title, feed.state.value.articles.single().title)
            assertEquals(graphArticle.title, bookmarks.state.value.bookmarks.single().title)
            assertEquals(graphArticle.title, article.state.value.article?.title)
            assertFalse(article.state.value.isLoading)
        } finally {
            application.close()
        }
    }
}
