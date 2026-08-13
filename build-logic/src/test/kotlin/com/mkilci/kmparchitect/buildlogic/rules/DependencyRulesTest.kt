package com.mkilci.kmparchitect.buildlogic.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rejection fixtures. A rule nobody has watched fail is not a rule, so every forbidden edge below
 * has a test proving it is caught, and the allowed edges have tests proving they are not.
 */
class DependencyRulesTest {

    private fun edge(from: String, to: String, configuration: String = "commonMainImplementation") =
        ProjectEdge(from, to, configuration)

    private fun rulesFired(vararg edges: ProjectEdge): List<String> =
        DependencyRules.violations(edges.toList()).map { it.rule }

    // ---- forbidden directions -------------------------------------------------------------

    @Test
    fun domain_may_not_depend_on_data() {
        assertEquals(
            listOf("forbidden-layer-dependency"),
            rulesFired(edge(":domain:feed", ":data:feed")),
        )
    }

    @Test
    fun domain_may_not_depend_on_presentation() {
        assertTrue("forbidden-layer-dependency" in rulesFired(edge(":domain:feed", ":presentation:feed")))
    }

    @Test
    fun presentation_may_not_depend_on_data() {
        assertTrue("forbidden-layer-dependency" in rulesFired(edge(":presentation:feed", ":data:feed")))
    }

    @Test
    fun core_may_not_depend_on_a_feature() {
        assertTrue("forbidden-layer-dependency" in rulesFired(edge(":core:mvi", ":domain:feed")))
    }

    @Test
    fun a_sample_may_not_depend_on_the_application_root() {
        assertTrue("forbidden-layer-dependency" in rulesFired(edge(":sample:feed:shared", ":app:shared")))
    }

    @Test
    fun a_sample_may_not_depend_on_its_own_data_module() {
        val violations = DependencyRules.violations(listOf(edge(":sample:feed:shared", ":data:feed")))

        assertEquals(listOf("forbidden-layer-dependency"), violations.map { it.rule })
        assertTrue(
            violations.single().message.contains("network, persistence"),
            "the load-bearing rule should explain what it protects, was: ${violations.single().message}",
        )
    }

    @Test
    fun fixtures_may_not_depend_on_data() {
        assertTrue("forbidden-layer-dependency" in rulesFired(edge(":fixtures:feed", ":data:feed")))
    }

    // ---- cross-feature --------------------------------------------------------------------

    @Test
    fun one_feature_may_not_depend_on_another() {
        val violations = DependencyRules.violations(
            listOf(edge(":presentation:bookmarks", ":presentation:article")),
        )

        assertTrue("cross-feature-dependency" in violations.map { it.rule })
    }

    @Test
    fun cross_feature_is_caught_even_between_layers_that_are_otherwise_legal() {
        // domain -> domain is a legal direction; the feature boundary is what fails here.
        assertEquals(
            listOf("cross-feature-dependency"),
            rulesFired(edge(":domain:bookmarks", ":domain:article")),
        )
    }

    // ---- fixtures consumer allowlist -------------------------------------------------------

    @Test
    fun the_production_app_may_not_consume_fixtures() {
        val violations = DependencyRules.violations(listOf(edge(":app:shared", ":fixtures:feed")))

        assertTrue("fixtures-consumer" in violations.map { it.rule })
        assertTrue(violations.any { it.message.contains("shipping fake data") })
    }

    @Test
    fun a_data_module_may_not_consume_fixtures() {
        assertTrue("fixtures-consumer" in rulesFired(edge(":data:feed", ":fixtures:feed")))
    }

    @Test
    fun presentation_may_not_consume_fixtures_from_production_sources() {
        val violations = DependencyRules.violations(
            listOf(edge(":presentation:feed", ":fixtures:feed", "commonMainImplementation")),
        )

        assertTrue("fixtures-consumer" in violations.map { it.rule })
        assertTrue(violations.any { it.message.contains("test source sets only") })
    }

    @Test
    fun a_sample_may_not_consume_another_features_fixtures() {
        assertTrue("fixtures-consumer" in rulesFired(edge(":sample:feed:shared", ":fixtures:article")))
    }

    // ---- api discipline --------------------------------------------------------------------

    @Test
    fun an_api_edge_outside_the_allowlist_is_rejected() {
        val violations = DependencyRules.violations(
            listOf(edge(":presentation:feed", ":core:ui", "commonMainApi")),
        )

        assertEquals(listOf("unjustified-api-dependency"), violations.map { it.rule })
    }

    @Test
    fun an_allowlisted_api_edge_passes() {
        val violations = DependencyRules.violations(
            edges = listOf(edge(":domain:feed", ":core:model", "commonMainApi")),
            apiAllowlist = setOf(":domain:feed" to ":core:model"),
        )

        assertEquals(emptyList(), violations)
    }

    // ---- edges that must NOT fire ------------------------------------------------------------

    @Test
    fun the_real_topology_of_this_repository_is_clean() {
        val edges = listOf(
            edge(":presentation:feed", ":domain:feed"),
            edge(":presentation:feed", ":core:mvi"),
            edge(":presentation:feed", ":core:ui"),
            edge(":presentation:feed", ":fixtures:feed", "commonTestImplementation"),
            edge(":fixtures:feed", ":domain:feed", "commonMainApi"),
            edge(":domain:feed", ":core:model", "commonMainApi"),
            edge(":core:navigation", ":core:mvi", "commonMainApi"),
            edge(":core:ui", ":core:designsystem"),
            edge(":data:feed", ":domain:feed"),
            edge(":data:feed", ":core:network"),
            edge(":sample:feed:shared", ":presentation:feed"),
            edge(":sample:feed:shared", ":fixtures:feed"),
            edge(":sample:feed:androidApp", ":sample:feed:shared"),
            edge(":app:shared", ":presentation:feed"),
            edge(":app:android", ":app:shared"),
        )

        val allowlist = setOf(
            ":fixtures:feed" to ":domain:feed",
            ":domain:feed" to ":core:model",
            ":core:navigation" to ":core:mvi",
        )

        assertEquals(
            emptyList(),
            DependencyRules.violations(edges, allowlist),
            "the production topology must pass its own rules",
        )
    }
}
