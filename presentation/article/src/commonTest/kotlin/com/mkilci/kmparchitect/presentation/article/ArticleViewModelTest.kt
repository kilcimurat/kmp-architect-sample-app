package com.mkilci.kmparchitect.presentation.article

import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.DefaultStateStore
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.domain.article.ObserveArticle
import com.mkilci.kmparchitect.domain.article.ObserveArticleBookmarkState
import com.mkilci.kmparchitect.domain.article.SetArticleBookmarked
import com.mkilci.kmparchitect.domain.article.ShareArticle
import com.mkilci.kmparchitect.fixtures.article.ArticleFixtures
import com.mkilci.kmparchitect.fixtures.article.FakeArticleRepository
import com.mkilci.kmparchitect.fixtures.article.RecordingSharer
import com.mkilci.kmparchitect.presentation.article.model.ArticleAction
import com.mkilci.kmparchitect.presentation.article.model.ArticleState
import com.mkilci.kmparchitect.presentation.article.model.ShareOutcome
import com.mkilci.kmparchitect.presentation.article.navigation.ArticleEffect
import com.mkilci.kmparchitect.presentation.article.viewmodel.ArticleViewModel
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

class ArticleViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        id: ArticleId = ArticleFixtures.known.id,
        sharer: RecordingSharer = RecordingSharer(),
        repository: FakeArticleRepository = FakeArticleRepository(),
    ) = ArticleViewModel(
        stateStore = DefaultStateStore(ArticleState()),
        articleId = id,
        observeArticle = ObserveArticle(repository),
        observeBookmarkState = ObserveArticleBookmarkState(repository),
        setArticleBookmarked = SetArticleBookmarked(repository),
        shareArticle = ShareArticle(sharer),
    )

    @Test
    fun a_known_article_reaches_state_as_a_render_only_model() = runTest {
        val state = viewModel().state.value

        assertEquals(ArticleFixtures.known.title, state.article?.title)
        assertEquals(false, state.isLoading)
        assertEquals(false, state.isMissing)
    }

    @Test
    fun an_unknown_id_produces_a_missing_state_not_a_crash() = runTest {
        val state = viewModel(id = ArticleId("nope")).state.value

        assertNull(state.article)
        assertTrue(state.isMissing)
    }

    @Test
    fun back_emits_a_typed_effect_synchronously_without_touching_state() = runTest {
        val vm = viewModel()
        val before = vm.state.value

        vm.onAction(ArticleAction.BackClicked)

        assertEquals(ArticleEffect.NavigateBack, withTimeout(1_000) { vm.effects.first() })
        assertEquals(before, vm.state.value)
    }

    @Test
    fun sharing_sends_the_article_to_the_sharer_and_reports_the_outcome_as_state() = runTest {
        val sharer = RecordingSharer(ShareResult.Shared)
        val vm = viewModel(sharer = sharer)

        vm.onAction(ArticleAction.ShareClicked)

        assertEquals(1, sharer.requests.size)
        assertEquals(ArticleFixtures.known.url, sharer.requests.single().url)
        assertEquals(ShareOutcome.Shared, vm.state.value.shareOutcome)
    }

    @Test
    fun an_unavailable_share_surface_is_reported_rather_than_silently_ignored() = runTest {
        val vm = viewModel(sharer = RecordingSharer(ShareResult.Unavailable))

        vm.onAction(ArticleAction.ShareClicked)

        assertEquals(ShareOutcome.Unavailable, vm.state.value.shareOutcome)
    }

    @Test
    fun dismissing_the_share_outcome_clears_it() = runTest {
        val vm = viewModel()
        vm.onAction(ArticleAction.ShareClicked)

        vm.onAction(ArticleAction.ShareOutcomeDismissed)

        assertNull(vm.state.value.shareOutcome)
    }

    @Test
    fun bookmarking_writes_through_storage_rather_than_optimistically_setting_state() = runTest {
        val vm = viewModel()
        assertEquals(false, vm.state.value.isBookmarked)

        vm.onAction(ArticleAction.BookmarkToggled)

        // The flag arrives back through the observed bookmark state, not from the action handler.
        assertTrue(vm.state.value.isBookmarked)
    }

    @Test
    fun toggling_twice_returns_to_the_unbookmarked_state() = runTest {
        val vm = viewModel()

        vm.onAction(ArticleAction.BookmarkToggled)
        vm.onAction(ArticleAction.BookmarkToggled)

        assertEquals(false, vm.state.value.isBookmarked)
    }
}
