package com.mkilci.kmparchitect.buildlogic.rules

/**
 * Rules over declared project edges. Pure functions on immutable data: no Gradle types here, so
 * every rule is unit tested directly, including the cases that must *not* fire.
 */
object DependencyRules {

    /** Layers a given layer may never depend on, in any configuration. */
    private val forbiddenTargets: Map<Layer, Set<Layer>> = mapOf(
        Layer.Core to setOf(Layer.Domain, Layer.Data, Layer.Presentation, Layer.Fixtures, Layer.Sample, Layer.App),
        Layer.Domain to setOf(Layer.Data, Layer.Presentation, Layer.Fixtures, Layer.Sample, Layer.App),
        Layer.Data to setOf(Layer.Presentation, Layer.Sample, Layer.App, Layer.Fixtures),
        Layer.Presentation to setOf(Layer.Data, Layer.Sample, Layer.App),
        Layer.Fixtures to setOf(Layer.Data, Layer.Presentation, Layer.Sample, Layer.App),
        // sample -> data is the load-bearing rule: it is what keeps network, persistence and
        // serialization out of the feature development loop.
        Layer.Sample to setOf(Layer.Data, Layer.App),
        Layer.App to emptySet(),
    )

    /**
     * @param infrastructureModules data-layer projects that are shared plumbing rather than a
     *   feature — a local store several features read, for example. They have no feature identity,
     *   so the cross-feature rule does not apply to them, and in exchange they may not depend on any
     *   feature at all. Declaring them explicitly keeps "it's infrastructure" from becoming the
     *   escape hatch that dissolves the feature boundary.
     */
    fun violations(
        edges: List<ProjectEdge>,
        apiAllowlist: Set<Pair<String, String>> = emptySet(),
        infrastructureModules: Set<String> = emptySet(),
    ): List<Violation> = edges.flatMap { edge ->
        listOfNotNull(
            layerDirection(edge),
            crossFeature(edge, infrastructureModules),
            infrastructureIndependence(edge, infrastructureModules),
            fixturesConsumer(edge),
            unjustifiedApi(edge, apiAllowlist),
        )
    }

    private fun layerDirection(edge: ProjectEdge): Violation? {
        val from = edge.fromModule
        val to = edge.toModule

        // Presentation test sources may use their own feature's fixtures; that exception is handled
        // by fixturesConsumer, so skip the blanket layer rule for it.
        if (from.layer == Layer.Presentation && to.layer == Layer.Fixtures && edge.isTestOnly) return null

        val forbidden = forbiddenTargets[from.layer].orEmpty()
        if (to.layer !in forbidden) return null

        val why = when {
            from.layer == Layer.Sample && to.layer == Layer.Data ->
                "A sample must not bind real repositories. This edge pulls network, persistence " +
                    "and serialization into the feature development loop, which is the cost this " +
                    "topology exists to remove."
            from.layer == Layer.Domain ->
                "Domain is pure business logic. It defines ports; it never reaches outward to an " +
                    "implementation of them."
            from.layer == Layer.Core ->
                "Core is feature-neutral infrastructure. Depending on a feature inverts that."
            else ->
                "${from.layer} must not depend on ${to.layer}."
        }
        return Violation(
            rule = "forbidden-layer-dependency",
            subject = "${edge.from} -> ${edge.to} (${edge.configuration})",
            message = why,
        )
    }

    private fun crossFeature(edge: ProjectEdge, infrastructure: Set<String>): Violation? {
        if (edge.from in infrastructure || edge.to in infrastructure) return null

        val fromFeature = edge.fromModule.feature ?: return null
        val toFeature = edge.toModule.feature ?: return null
        if (fromFeature == toFeature) return null

        return Violation(
            rule = "cross-feature-dependency",
            subject = "${edge.from} -> ${edge.to} (${edge.configuration})",
            message = "Feature '$fromFeature' depends on feature '$toFeature'. Cross-feature " +
                "decisions belong to app/shared; one such edge collapses the isolation claim for " +
                "both features.",
        )
    }

    /**
     * The price of having no feature identity: an infrastructure module may be shared by every
     * feature, so it must know about none of them. Without this, "infrastructure" would be a label
     * anyone could attach to a module to smuggle a cross-feature dependency past the previous rule.
     */
    private fun infrastructureIndependence(edge: ProjectEdge, infrastructure: Set<String>): Violation? {
        if (edge.from !in infrastructure) return null
        if (edge.toModule.feature == null) return null
        if (edge.to in infrastructure) return null

        return Violation(
            rule = "infrastructure-depends-on-feature",
            subject = "${edge.from} -> ${edge.to} (${edge.configuration})",
            message = "${edge.from} is declared shared infrastructure, so it may be used by any " +
                "feature and must therefore depend on none. Move the feature-specific part into " +
                "the feature's own data module.",
        )
    }

    /**
     * Inbound allowlist for fixtures. Fixtures are ordinary modules, so without this rule the
     * production app can bind fake data and ship it, and every direction-based check still passes.
     */
    private fun fixturesConsumer(edge: ProjectEdge): Violation? {
        if (edge.toModule.layer != Layer.Fixtures) return null

        val consumer = edge.fromModule
        val sameFeature = consumer.feature != null && consumer.feature == edge.toModule.feature

        val allowed = when {
            consumer.layer == Layer.Sample && sameFeature -> true
            consumer.layer == Layer.Presentation && sameFeature && edge.isTestOnly -> true
            consumer.layer == Layer.Fixtures && consumer.path == edge.to -> true
            else -> false
        }
        if (allowed) return null

        val why = if (consumer.layer == Layer.Presentation && sameFeature) {
            "Presentation may use its own fixtures from test source sets only; '${edge.configuration}' " +
                "ships them in production code."
        } else {
            "Only :presentation:${edge.toModule.feature} (test sources) and " +
                ":sample:${edge.toModule.feature}:* may consume these fixtures. Any other consumer " +
                "risks shipping fake data in a real application."
        }
        return Violation(
            rule = "fixtures-consumer",
            subject = "${edge.from} -> ${edge.to} (${edge.configuration})",
            message = why,
        )
    }

    /**
     * Every `api` edge re-exports a dependency, so an ABI change there recompiles every consumer.
     * They are allowed, but only deliberately: each one is listed in `config/api-allowlist.txt`
     * with a reason, which makes widening the blast radius a reviewable diff.
     */
    private fun unjustifiedApi(edge: ProjectEdge, allowlist: Set<Pair<String, String>>): Violation? {
        if (!edge.isApi) return null
        if (edge.isTestOnly) return null
        if (edge.from to edge.to in allowlist) return null

        return Violation(
            rule = "unjustified-api-dependency",
            subject = "${edge.from} -> ${edge.to} (${edge.configuration})",
            message = "An `api` edge re-exports this dependency, so every consumer recompiles when " +
                "its ABI changes. Use `implementation`, or add the pair to config/api-allowlist.txt " +
                "with the reason it is part of this module's own API.",
        )
    }
}
