package com.mkilci.kmparchitect.buildlogic

import com.mkilci.kmparchitect.buildlogic.rules.DependencyRules
import com.mkilci.kmparchitect.buildlogic.rules.ProjectEdge
import com.mkilci.kmparchitect.buildlogic.rules.SourceFile
import com.mkilci.kmparchitect.buildlogic.rules.SourceRules
import com.mkilci.kmparchitect.buildlogic.rules.Violation
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Checks declared dependency edges and Kotlin sources against the architecture rules.
 *
 * Everything it needs is captured at configuration time as immutable strings, so the task never
 * touches a `Project` at execution time and stays configuration-cache compatible.
 */
@CacheableTask
abstract class ArchitectureCheckTask : DefaultTask() {

    /** `from|to|configuration`, one declared project dependency each. */
    @get:Input
    abstract val edges: ListProperty<String>

    /** `projectPath|absoluteSourceRoot`. */
    @get:Input
    abstract val sourceRoots: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apiAllowlist: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val infrastructureModules: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val parsedEdges = edges.get().map { line ->
            val (from, to, configuration) = line.split('|')
            ProjectEdge(from, to, configuration)
        }
        val allowlist = parseAllowlist(apiAllowlist.get().asFile)
        val infrastructure = parseLines(infrastructureModules.get().asFile)
        val sources = readSources()

        val violations = DependencyRules.violations(parsedEdges, allowlist, infrastructure) +
            SourceRules.violations(sources)

        writeReport(parsedEdges.size, sources.size, violations)

        if (violations.isNotEmpty()) {
            val byRule = violations.groupBy { it.rule }
            val summary = byRule.entries.joinToString("\n\n") { (rule, items) ->
                "$rule (${items.size}):\n" + items.joinToString("\n") { "  ${it.subject}\n      ${it.message}" }
            }
            throw GradleException(
                "architectureCheck found ${violations.size} violation(s) across " +
                    "${byRule.size} rule(s):\n\n$summary\n\nReport: ${report.get().asFile}",
            )
        }
    }

    private fun parseLines(file: File): Set<String> =
        file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun parseAllowlist(file: File): Set<Pair<String, String>> =
        file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split("->").map(String::trim)
                require(parts.size == 2) { "Malformed api-allowlist entry: '$line'" }
                parts[0] to parts[1]
            }
            .toSet()

    private fun readSources(): List<SourceFile> = sourceRoots.get().flatMap { entry ->
        val (projectPath, root) = entry.split('|', limit = 2)
        val rootFile = File(root)
        if (!rootFile.exists()) return@flatMap emptyList()

        rootFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                SourceFile(
                    projectPath = projectPath,
                    relativePath = file.relativeTo(rootFile).invariantSeparatorsPath,
                    content = file.readText(),
                )
            }
            .toList()
    }

    private fun writeReport(edgeCount: Int, sourceCount: Int, violations: List<Violation>) {
        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("architectureCheck")
                appendLine("edges inspected:   $edgeCount")
                appendLine("sources inspected: $sourceCount")
                appendLine("violations:        ${violations.size}")
                if (violations.isNotEmpty()) {
                    appendLine()
                    violations.forEach { appendLine(it.toString()) }
                }
            },
        )
    }
}
