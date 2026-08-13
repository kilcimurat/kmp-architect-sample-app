package com.mkilci.kmparchitect.buildlogic.rules

/** A Kotlin source file with enough context to attribute a violation to a module. */
data class SourceFile(
    val projectPath: String,
    val relativePath: String,
    val content: String,
) {
    val isTestSource: Boolean = relativePath.contains("Test/") || relativePath.contains("test/")
}

/**
 * Checks that dependency metadata cannot see: a module can import a forbidden type from a
 * dependency it is legitimately allowed to have, and a reducer's purity is invisible to Gradle.
 */
object SourceRules {

    private data class ImportRule(val marker: String, val why: String)

    private val domainForbidden = listOf(
        ImportRule("androidx.compose", "Domain is pure Kotlin; Compose belongs to presentation."),
        ImportRule("org.koin", "Domain declares constructors, not DI definitions."),
        ImportRule("android.", "Domain must not see the Android framework."),
        ImportRule("platform.UIKit", "Domain must not see UIKit."),
        ImportRule("androidx.lifecycle", "ViewModel lifecycle is a presentation concern."),
        ImportRule("io.ktor", "Domain speaks business results, not transports."),
        ImportRule("app.cash.sqldelight", "Domain speaks business results, not storage."),
        ImportRule("kotlinx.serialization", "Serialization is a data-layer concern."),
        ImportRule("com.mkilci.kmparchitect.data", "Domain defines ports; it never imports their implementations."),
        ImportRule("com.mkilci.kmparchitect.presentation", "Domain must not know about UI."),
    )

    private val presentationForbidden = listOf(
        ImportRule("com.mkilci.kmparchitect.data", "Presentation talks to domain ports, never to data implementations."),
        ImportRule("io.ktor", "Presentation must not reach the network directly."),
        ImportRule("app.cash.sqldelight", "Presentation must not reach storage directly."),
    )

    private val dataForbidden = listOf(
        ImportRule("com.mkilci.kmparchitect.presentation", "Data must not know about UI state or presentation."),
        ImportRule("androidx.compose", "Data must not contain Compose UI."),
        ImportRule("org.jetbrains.compose", "Data must not contain Compose UI."),
        ImportRule("androidx.navigation", "Navigation belongs to presentation graphs and the application root."),
    )

    private val coreForbidden = listOf(
        ImportRule("com.mkilci.kmparchitect.domain", "Core is feature-neutral."),
        ImportRule("com.mkilci.kmparchitect.data", "Core is feature-neutral."),
        ImportRule("com.mkilci.kmparchitect.presentation", "Core is feature-neutral."),
        ImportRule("com.mkilci.kmparchitect.fixtures", "Core must not depend on test doubles."),
        ImportRule("com.mkilci.kmparchitect.sample", "Core is feature-neutral."),
        ImportRule("com.mkilci.kmparchitect.app", "Core must not depend on the application root."),
    )

    private val fixturesForbidden = listOf(
        ImportRule("io.ktor", "A fixture that performs real I/O is not deterministic."),
        ImportRule("app.cash.sqldelight", "A fixture that performs real I/O is not deterministic."),
        ImportRule("com.mkilci.kmparchitect.data", "Fixtures replace data implementations; they never wrap them."),
        ImportRule("kotlin.random.Random", "Fixtures must use fixed or explicitly seeded randomness."),
        ImportRule("kotlin.time.Clock", "Fixtures must use fixed or injected time."),
        ImportRule("kotlinx.datetime.Clock", "Fixtures must use fixed or injected time."),
        ImportRule("java.time.Clock", "Fixtures must use fixed or injected time."),
    )

    private val viewModelControllerImports = listOf(
        "androidx.navigation",
        "android.app.Activity",
        "android.content.Context",
        "platform.UIKit",
    )

    /** Markers that make a reducer impure. Reducers may be re-invoked by `MutableStateFlow.update`. */
    private val reducerForbidden = listOf(
        ImportRule("viewModelScope", "A reducer must not start coroutines."),
        ImportRule("launch(", "A reducer must not start coroutines."),
        ImportRule("runBlocking", "A reducer must not block."),
        ImportRule("Repository", "A reducer must not call repositories."),
        ImportRule("repository.", "A reducer must not call repositories."),
        ImportRule("getKoin", "A reducer must not resolve dependencies."),
        ImportRule("Clock.", "A reducer must not read the clock; pass time in through the event."),
        ImportRule("Random", "A reducer must be deterministic."),
        ImportRule("sendEffect", "A reducer must not emit effects."),
        ImportRule("navigate", "A reducer must not navigate."),
        ImportRule(".add(", "A reducer must not mutate a collection owned by the previous state."),
        ImportRule(".remove(", "A reducer must not mutate a collection owned by the previous state."),
        ImportRule(".clear(", "A reducer must not mutate a collection owned by the previous state."),
    )

    private val effectInStateHolder = Regex("""MutableStateFlow\s*<[^>]*Effect""")

    fun violations(files: List<SourceFile>): List<Violation> = files.flatMap { file ->
        val layer = ModuleId(file.projectPath).layer
        buildList {
            val importRules = when {
                layer == Layer.Domain -> domainForbidden
                layer == Layer.Data -> dataForbidden
                layer == Layer.Presentation && !file.isTestSource -> presentationForbidden
                layer == Layer.Core -> coreForbidden
                layer == Layer.Fixtures -> fixturesForbidden
                else -> emptyList()
            }
            importRules.forEach { rule ->
                if (importsMarker(file.content, rule.marker)) {
                    add(
                        Violation(
                            rule = "forbidden-import",
                            subject = "${file.projectPath} ${file.relativePath}",
                            message = "imports '${rule.marker}' — ${rule.why}",
                        ),
                    )
                }
            }

            if (effectInStateHolder.containsMatchIn(file.content)) {
                add(
                    Violation(
                        rule = "replayed-effect-storage",
                        subject = "${file.projectPath} ${file.relativePath}",
                        message = "stores an Effect in a MutableStateFlow. A state holder replays its " +
                            "current value, so a consumed navigation command fires again on recreation.",
                    ),
                )
            }

            val controllerImport = viewModelControllerImports.firstOrNull { marker ->
                importsMarker(file.content, marker)
            }
            if (!file.isTestSource && file.content.contains("MviViewModel<") && controllerImport != null) {
                add(
                    Violation(
                        rule = "viewmodel-holds-navigator",
                        subject = "${file.projectPath} ${file.relativePath}",
                        message = "a ViewModel file imports '$controllerImport'. Route graphs/native hosts own " +
                            "controllers; ViewModels emit typed effects and hold no platform navigator.",
                    ),
                )
            }

            if (layer == Layer.Presentation && !file.isTestSource &&
                file.content.contains("@Composable") && resolvesRepositoryFromComposable(file.content)
            ) {
                add(
                    Violation(
                        rule = "composable-repository-lookup",
                        subject = "${file.projectPath} ${file.relativePath}",
                        message = "a composable resolves a repository from the DI container. Route composables " +
                            "may resolve ViewModels; repositories stay behind domain use cases.",
                    ),
                )
            }

            if (!file.isTestSource && file.content.contains("MviViewModel<") &&
                file.content.contains("sendEffect(") && importsMarker(file.content, "kotlinx.coroutines.GlobalScope")
            ) {
                add(
                    Violation(
                        rule = "unowned-effect-scope",
                        subject = "${file.projectPath} ${file.relativePath}",
                        message = "Effects must not originate from GlobalScope, which can outlive the ViewModel transport.",
                    ),
                )
            }

            addAll(reducerViolations(file))
        }
    }

    private fun reducerViolations(file: SourceFile): List<Violation> =
        ReducerExtractor.extract(file.content).flatMap { reducer ->
            reducerForbidden.mapNotNull { rule ->
                if (!reducer.body.contains(rule.marker)) return@mapNotNull null
                Violation(
                    rule = "impure-reducer",
                    subject = "${file.projectPath} ${file.relativePath}:${reducer.line}",
                    message = "reduce() body contains '${rule.marker}' — ${rule.why}",
                )
            }
        }

    /**
     * Matches an actual import statement rather than any occurrence of the text, so a comment
     * explaining why Compose is forbidden does not itself become a violation.
     */
    private fun importsMarker(content: String, marker: String): Boolean =
        content.lineSequence().any { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") && trimmed.removePrefix("import ").trimStart().startsWith(marker)
        }

    private fun resolvesRepositoryFromComposable(content: String): Boolean =
        content.contains("getKoin().get<") && content.contains("Repository>") ||
            content.contains("koinInject<") && content.contains("Repository>")
}
