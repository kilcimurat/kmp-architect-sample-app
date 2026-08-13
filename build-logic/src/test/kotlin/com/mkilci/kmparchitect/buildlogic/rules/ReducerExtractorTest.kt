package com.mkilci.kmparchitect.buildlogic.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReducerExtractorTest {

    @Test
    fun finds_a_block_bodied_reducer() {
        val source = """
            data object Started : FeedEvent {
                override fun reduce(oldState: FeedState): FeedState {
                    return oldState.copy(isRefreshing = true)
                }
            }
        """.trimIndent()

        val reducers = ReducerExtractor.extract(source)

        assertEquals(1, reducers.size)
        assertTrue(reducers.single().body.contains("isRefreshing = true"))
        assertEquals(false, reducers.single().isExpressionBody)
    }

    @Test
    fun finds_a_single_line_expression_bodied_reducer() {
        val source = """
            data object Dismissed : FeedEvent {
                override fun reduce(oldState: FeedState) = oldState.copy(notice = null)
            }
        """.trimIndent()

        val reducers = ReducerExtractor.extract(source)

        assertEquals(1, reducers.size)
        assertTrue(reducers.single().isExpressionBody)
        assertTrue(reducers.single().body.contains("notice = null"))
    }

    @Test
    fun finds_a_multi_line_expression_bodied_reducer() {
        val source = """
            data class Loaded(val articles: List<FeedArticleUi>) : FeedEvent {
                override fun reduce(oldState: FeedState) =
                    oldState.copy(
                        articles = articles,
                        isLoading = false,
                    )
            }
        """.trimIndent()

        val reducers = ReducerExtractor.extract(source)

        assertEquals(1, reducers.size)
        assertTrue(reducers.single().body.contains("isLoading = false"))
    }

    @Test
    fun finds_reducers_in_a_file_whose_name_says_nothing_about_events() {
        // The whole point of scanning signatures: this would live in FeedContract.kt.
        val source = """
            sealed interface FeedEvent : ScreenEvent<FeedState> {
                data object A : FeedEvent {
                    override fun reduce(oldState: FeedState) = oldState
                }
                data object B : FeedEvent {
                    override fun reduce(oldState: FeedState): FeedState { return oldState }
                }
            }
        """.trimIndent()

        assertEquals(2, ReducerExtractor.extract(source).size)
    }

    @Test
    fun does_not_capture_side_effects_that_live_outside_a_reducer() {
        val source = """
            class FeedViewModel : MviViewModel<FeedState, FeedEvent, FeedEffect>(store) {
                fun refresh() {
                    viewModelScope.launch { repository.refresh() }
                }
            }

            data object Started : FeedEvent {
                override fun reduce(oldState: FeedState) = oldState.copy(isRefreshing = true)
            }
        """.trimIndent()

        val reducers = ReducerExtractor.extract(source)

        assertEquals(1, reducers.size)
        assertTrue(
            !reducers.single().body.contains("viewModelScope"),
            "orchestration in a non-reducer method must not be attributed to the reducer",
        )
    }

    @Test
    fun reports_the_declaration_line_so_the_failure_is_actionable() {
        val source = "package x\n\nclass A {\n    override fun reduce(s: S) = s\n}\n"

        assertEquals(4, ReducerExtractor.extract(source).single().line)
    }

    @Test
    fun a_file_with_no_reducer_yields_nothing() {
        assertEquals(emptyList(), ReducerExtractor.extract("fun reduceNoise() = Unit"))
    }
}
