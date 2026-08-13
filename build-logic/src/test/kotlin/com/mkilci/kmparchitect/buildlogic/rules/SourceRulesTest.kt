package com.mkilci.kmparchitect.buildlogic.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceRulesTest {

    private fun file(project: String, path: String = "src/commonMain/kotlin/F.kt", content: String) =
        SourceFile(project, path, content)

    private fun rulesFired(vararg files: SourceFile) =
        SourceRules.violations(files.toList()).map { it.rule }

    @Test
    fun domain_may_not_import_compose() {
        assertTrue(
            "forbidden-import" in rulesFired(
                file(":domain:feed", content = "import androidx.compose.runtime.Composable\n"),
            ),
        )
    }

    @Test
    fun domain_may_not_import_koin() {
        assertTrue("forbidden-import" in rulesFired(file(":domain:feed", content = "import org.koin.dsl.module\n")))
    }

    @Test
    fun domain_may_not_import_uikit_or_android() {
        assertTrue("forbidden-import" in rulesFired(file(":domain:feed", content = "import platform.UIKit.UIDevice\n")))
        assertTrue("forbidden-import" in rulesFired(file(":domain:feed", content = "import android.content.Context\n")))
    }

    @Test
    fun presentation_may_not_import_a_data_implementation() {
        assertTrue(
            "forbidden-import" in rulesFired(
                file(":presentation:feed", content = "import com.mkilci.kmparchitect.data.feed.DefaultFeedRepository\n"),
            ),
        )
    }

    @Test
    fun core_may_not_import_a_feature() {
        assertTrue(
            "forbidden-import" in rulesFired(
                file(":core:mvi", content = "import com.mkilci.kmparchitect.domain.feed.FeedRepository\n"),
            ),
        )
    }

    @Test
    fun fixtures_may_not_perform_real_io() {
        assertTrue("forbidden-import" in rulesFired(file(":fixtures:feed", content = "import io.ktor.client.HttpClient\n")))
    }

    @Test
    fun a_comment_mentioning_a_forbidden_package_is_not_a_violation() {
        val content = """
            // Domain must never import androidx.compose or org.koin.
            package com.mkilci.kmparchitect.domain.feed
        """.trimIndent()

        assertEquals(emptyList(), rulesFired(file(":domain:feed", content = content)))
    }

    @Test
    fun storing_an_effect_in_a_state_holder_is_rejected() {
        val content = "val effects = MutableStateFlow<FeedEffect?>(null)\n"

        val violations = SourceRules.violations(listOf(file(":presentation:feed", content = content)))

        assertTrue("replayed-effect-storage" in violations.map { it.rule })
        assertTrue(violations.any { it.message.contains("fires again on recreation") })
    }

    @Test
    fun a_viewmodel_holding_a_navigator_is_rejected() {
        val content = """
            import androidx.navigation.NavHostController
            class FeedViewModel(private val nav: NavHostController) :
                MviViewModel<FeedState, FeedEvent, FeedEffect>(store)
        """.trimIndent()

        assertTrue("viewmodel-holds-navigator" in rulesFired(file(":presentation:feed", content = content)))
    }

    @Test
    fun a_reducer_that_calls_a_repository_is_rejected() {
        val content = """
            data object Started : FeedEvent {
                override fun reduce(oldState: FeedState): FeedState {
                    repository.save()
                    return oldState
                }
            }
        """.trimIndent()

        val violations = SourceRules.violations(listOf(file(":presentation:feed", content = content)))

        assertTrue("impure-reducer" in violations.map { it.rule })
    }

    @Test
    fun a_reducer_that_reads_the_clock_is_rejected() {
        val content = """
            data object Tick : FeedEvent {
                override fun reduce(oldState: FeedState) = oldState.copy(at = Clock.System.now())
            }
        """.trimIndent()

        assertTrue("impure-reducer" in rulesFired(file(":presentation:feed", content = content)))
    }

    @Test
    fun a_reducer_that_mutates_the_previous_states_collection_is_rejected() {
        val content = """
            data class Added(val item: String) : FeedEvent {
                override fun reduce(oldState: FeedState): FeedState {
                    oldState.items.add(item)
                    return oldState
                }
            }
        """.trimIndent()

        assertTrue("impure-reducer" in rulesFired(file(":presentation:feed", content = content)))
    }

    @Test
    fun orchestration_outside_a_reducer_is_allowed() {
        val content = """
            class FeedViewModel : MviViewModel<FeedState, FeedEvent, FeedEffect>(store) {
                private fun refresh() {
                    viewModelScope.launch { sendEvent(FeedEvent.Started) }
                }
            }
        """.trimIndent()

        assertEquals(
            emptyList(),
            rulesFired(file(":presentation:feed", content = content)),
            "a ViewModel is exactly where orchestration belongs",
        )
    }

    @Test
    fun the_pure_reducers_this_repository_actually_ships_pass() {
        val content = """
            sealed interface FeedEvent : ScreenEvent<FeedState> {
                data class Loaded(val articles: List<FeedArticleUi>) : FeedEvent {
                    override fun reduce(oldState: FeedState) =
                        oldState.copy(articles = articles, isLoading = false)
                }
                data object RefreshStarted : FeedEvent {
                    override fun reduce(oldState: FeedState) =
                        oldState.copy(isRefreshing = true, notice = null)
                }
            }
        """.trimIndent()

        assertEquals(emptyList(), rulesFired(file(":presentation:feed", content = content)))
    }
}
