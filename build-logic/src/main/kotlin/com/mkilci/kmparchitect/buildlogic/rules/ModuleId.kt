package com.mkilci.kmparchitect.buildlogic.rules

/** Where a Gradle project sits in the architecture, derived from its path alone. */
enum class Layer {
    Core,
    Domain,
    Data,
    Fixtures,
    Presentation,
    Sample,
    App,
    Unknown,
}

/**
 * A Gradle project path classified by layer and feature.
 *
 * Classification is by path because that is what both Gradle and a reader see first; if a module's
 * path does not say what it is, the topology is already unclear.
 */
data class ModuleId(val path: String) {

    val segments: List<String> = path.split(":").filter { it.isNotBlank() }

    val layer: Layer = when (segments.firstOrNull()) {
        "core" -> Layer.Core
        "domain" -> Layer.Domain
        "data" -> Layer.Data
        "fixtures" -> Layer.Fixtures
        "presentation" -> Layer.Presentation
        "sample" -> Layer.Sample
        "app" -> Layer.App
        else -> Layer.Unknown
    }

    /**
     * The feature a project belongs to, or null for feature-neutral projects.
     *
     * `:data:articlestore` is deliberately treated as a feature named `articlestore`: it is a
     * focused infrastructure module, and calling it a feature keeps the feature-to-feature rule
     * from having a special case that could be abused later.
     */
    val feature: String? = when (layer) {
        Layer.Domain, Layer.Data, Layer.Fixtures, Layer.Presentation, Layer.Sample ->
            segments.getOrNull(1)
        else -> null
    }

    override fun toString(): String = path
}

/**
 * One declared dependency edge, as Gradle sees it before resolution.
 *
 * [configuration] matters as much as direction: `commonMainApi` and `commonMainImplementation` are
 * the same arrow with very different recompilation consequences, and `commonTestImplementation` is
 * an edge production code never travels.
 */
data class ProjectEdge(
    val from: String,
    val to: String,
    val configuration: String,
) {
    val fromModule: ModuleId get() = ModuleId(from)
    val toModule: ModuleId get() = ModuleId(to)

    /** `commonMainApi`, `androidMainApi`, or a plain `api`. */
    val isApi: Boolean = configuration == "api" || configuration.endsWith("Api")

    /** Any test source set: `commonTestImplementation`, `androidHostTestImplementation`, … */
    val isTestOnly: Boolean = configuration.contains("Test")
}

/** One declared external module dependency, before resolution. */
data class ExternalEdge(
    val from: String,
    val coordinate: String,
    val configuration: String,
) {
    val fromModule: ModuleId get() = ModuleId(from)
    val isApi: Boolean = configuration == "api" || configuration.endsWith("Api")
    val isTestOnly: Boolean = configuration.contains("Test")
}

data class Violation(
    val rule: String,
    val subject: String,
    val message: String,
) {
    override fun toString(): String = "[$rule] $subject\n    $message"
}
