package com.mkilci.kmparchitect.buildlogic

/**
 * Deterministic name derivation from Gradle project paths.
 *
 * These are pure functions on purpose: namespaces and Apple framework names must be stable and
 * unique, and getting them wrong is the kind of build break that only shows up in Xcode. They are
 * unit tested in `src/test/kotlin` without executing Gradle.
 */
internal object ProjectNaming {

    const val BASE_PACKAGE: String = "com.mkilci.kmparchitect"

    /**
     * `:core:mvi` -> `com.mkilci.kmparchitect.core.mvi`
     *
     * Segments are lowercased and stripped of characters that are illegal in a package name, so a
     * hyphenated project directory cannot silently produce an invalid namespace.
     */
    fun namespaceFor(projectPath: String, basePackage: String = BASE_PACKAGE): String {
        val segments = segmentsOf(projectPath).map { segment ->
            segment.lowercase().filter { it.isLetterOrDigit() }
        }.filter { it.isNotEmpty() }

        require(segments.isNotEmpty()) { "Cannot derive a namespace from project path '$projectPath'" }
        return (listOf(basePackage) + segments).joinToString(".")
    }

    /**
     * Apple framework base names must be unique across the whole repository and valid Swift
     * identifiers. Several projects are named `shared`, so `project.name` alone is not enough.
     *
     * `:app:shared`           -> `AppShared`
     * `:sample:feed:shared`   -> `FeedSample`
     * anything else           -> PascalCase of every segment
     */
    fun frameworkBaseNameFor(projectPath: String): String {
        val segments = segmentsOf(projectPath)
        require(segments.isNotEmpty()) { "Cannot derive a framework name from project path '$projectPath'" }

        if (segments.size == 3 && segments[0] == "sample" && segments[2] == "shared") {
            return pascalCase(segments[1]) + "Sample"
        }
        return segments.joinToString("") { pascalCase(it) }
    }

    private fun segmentsOf(projectPath: String): List<String> =
        projectPath.split(":").filter { it.isNotBlank() }

    private fun pascalCase(segment: String): String =
        segment.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }
}
