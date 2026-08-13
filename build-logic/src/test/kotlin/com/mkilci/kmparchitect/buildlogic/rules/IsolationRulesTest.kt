package com.mkilci.kmparchitect.buildlogic.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The isolation gate is the architecture's central claim, so both halves are proven here: a clean
 * graph passes on either platform, and every way a sample can widen is rejected by name.
 */
class IsolationRulesTest {

    private val allowlist = IsolationRules.parseAllowlist(
        """
        # Projects the feed sample's resolved graph is allowed to contain.
        :sample:feed:shared
        :presentation:feed
        :domain:feed
        :fixtures:feed
        :core:mvi

        """.trimIndent().lines(),
    )

    private val forbidden = setOf("io.ktor:", "app.cash.sqldelight:")

    private val androidGraph = setOf(
        ":sample:feed:androidApp",
        ":sample:feed:shared",
        ":presentation:feed",
        ":domain:feed",
        ":fixtures:feed",
        ":core:mvi",
    )

    /** The iOS graph is rooted at the framework module, so it lacks the Android executable. */
    private val iosGraph = setOf(
        ":sample:feed:shared",
        ":presentation:feed",
        ":domain:feed",
        ":fixtures:feed",
        ":core:mvi",
    )

    private fun evaluate(
        root: String,
        projects: Set<String>,
        modules: Set<String> = emptySet(),
        allowed: Set<String> = allowlist,
    ) = IsolationRules.evaluate(root, projects, modules, allowed, forbidden)

    @Test
    fun `comments and blank lines are not allowlist entries`() {
        assertEquals(
            setOf(":sample:feed:shared", ":presentation:feed", ":domain:feed", ":fixtures:feed", ":core:mvi"),
            allowlist,
        )
    }

    @Test
    fun `clean android graph passes`() {
        val verdict = evaluate(":sample:feed:androidApp", androidGraph)

        assertEquals(emptyList(), verdict.problems)
        assertEquals(5, verdict.actual.size)
    }

    @Test
    fun `the same allowlist accepts the ios graph rooted at the framework module`() {
        val verdict = evaluate(":sample:feed:shared", iosGraph)

        assertEquals(emptyList(), verdict.problems)
        // The root is not reported as stale merely because the iOS graph is rooted there.
        assertTrue(verdict.stale.isEmpty())
        assertEquals(4, verdict.actual.size)
    }

    @Test
    fun `sample to data is rejected on android and the offender is named`() {
        val verdict = evaluate(":sample:feed:androidApp", androidGraph + ":data:feed")

        assertEquals(listOf(":data:feed"), verdict.unexpected)
        assertTrue(verdict.problems.single().contains(":data:feed"))
    }

    @Test
    fun `sample to data is rejected on ios too`() {
        val verdict = evaluate(":sample:feed:shared", iosGraph + ":data:feed")

        assertEquals(listOf(":data:feed"), verdict.unexpected)
    }

    @Test
    fun `another feature reaching the sample is rejected`() {
        val verdict = evaluate(":sample:feed:shared", iosGraph + ":presentation:article")

        assertEquals(listOf(":presentation:article"), verdict.unexpected)
    }

    @Test
    fun `a forbidden external is rejected even without a new project node`() {
        val verdict = evaluate(
            root = ":sample:feed:shared",
            projects = iosGraph,
            modules = setOf("org.jetbrains.kotlinx:kotlinx-coroutines-core", "io.ktor:ktor-client-core"),
        )

        assertEquals(emptyList(), verdict.unexpected)
        assertEquals(listOf("io.ktor:ktor-client-core"), verdict.forbiddenExternals)
    }

    @Test
    fun `an allowlist entry that no longer resolves is reported as stale`() {
        val verdict = evaluate(":sample:feed:shared", iosGraph - ":core:mvi")

        assertEquals(listOf(":core:mvi"), verdict.stale)
    }
}
