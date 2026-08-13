package com.mkilci.kmparchitect.buildlogic.rules

/**
 * The outcome of comparing one resolved sample graph against its allowlist.
 *
 * [actual] is reported even when nothing is wrong: the number of projects a feature developer
 * actually compiles is the architecture's headline claim, so it belongs in the report rather than
 * only in a failure message.
 */
data class IsolationVerdict(
    val actual: List<String>,
    val unexpected: List<String>,
    val stale: List<String>,
    val forbiddenExternals: List<String>,
) {
    val problems: List<String> = buildList {
        unexpected.forEach { add("unexpected project on the resolved graph: $it") }
        forbiddenExternals.forEach { add("forbidden external dependency reached the sample: $it") }
        stale.forEach { add("allowlist entry no longer on the graph (remove it): $it") }
    }
}

/**
 * Pure comparison of a resolved graph against an allowlist, kept out of the Gradle task so it can be
 * unit tested without executing a build.
 *
 * One allowlist per sample serves both platforms. The Android graph is resolved from that sample's
 * `androidApp` executable, the iOS graph from its `shared` framework module, so the two roots differ
 * while the projects underneath must be identical — which is exactly the claim being made. Removing
 * the root from both sides is what lets a single file express that.
 */
object IsolationRules {

    /** Allowlist files are reviewed by humans, so `#` comments and blank lines are expected. */
    fun parseAllowlist(lines: List<String>): Set<String> = lines
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun evaluate(
        rootPath: String,
        resolvedProjects: Set<String>,
        resolvedModules: Set<String>,
        allowlist: Set<String>,
        forbiddenModulePrefixes: Set<String>,
    ): IsolationVerdict {
        // A graph always contains its own root; listing it would say nothing about isolation. The
        // iOS root (`:sample:<f>:shared`) is a legitimate allowlist entry for the Android graph, so
        // it is dropped from the allowlist too rather than reported as stale.
        val actual = resolvedProjects - rootPath
        val allowed = allowlist - rootPath

        return IsolationVerdict(
            actual = actual.sorted(),
            unexpected = (actual - allowed).sorted(),
            stale = (allowed - actual).sorted(),
            forbiddenExternals = resolvedModules
                .filter { module -> forbiddenModulePrefixes.any { module.startsWith(it) } }
                .sorted(),
        )
    }
}
