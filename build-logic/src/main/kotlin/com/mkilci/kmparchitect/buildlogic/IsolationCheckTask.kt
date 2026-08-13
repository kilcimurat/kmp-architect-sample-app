package com.mkilci.kmparchitect.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Compares a sample's **resolved** dependency graph against a checked-in allowlist.
 *
 * Declared edges and folder shape are not evidence of isolation — a transitive edge two projects
 * away is invisible to both. This task resolves a real classpath and names every project on it.
 *
 * Widening a sample then becomes a diff to `isolation-allowlist.txt` that a reviewer sees, instead
 * of a silent regression that only shows up as a slower build months later.
 */
abstract class IsolationCheckTask : DefaultTask() {

    @get:Input
    abstract val samplePath: Property<String>

    @get:Input
    abstract val configurationName: Property<String>

    /** Every `project(...)` node on the resolved graph, root included. */
    @get:Input
    abstract val resolvedProjects: SetProperty<String>

    /** Every external `group:name` on the resolved graph. */
    @get:Input
    abstract val resolvedModules: SetProperty<String>

    /**
     * External coordinates that must never reach a sample. These are the libraries whose absence is
     * the build-time saving: if they are on the graph, the sample is compiling the data stack.
     */
    @get:Input
    abstract val forbiddenModulePrefixes: SetProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowlistFile: RegularFileProperty

    /** Repository-relative path, captured at configuration time so the task never reads `project`. */
    @get:Input
    abstract val allowlistDisplayPath: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val allowlistPath = allowlistFile.get().asFile
        val allowed = allowlistPath.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val root = samplePath.get()
        val actual = resolvedProjects.get() - root
        val unexpected = (actual - allowed).sorted()
        val stale = (allowed - actual).sorted()

        val forbiddenExternals = resolvedModules.get()
            .filter { module -> forbiddenModulePrefixes.get().any { module.startsWith(it) } }
            .sorted()

        writeReport(root, actual.sorted(), unexpected, stale, forbiddenExternals)

        val problems = buildList {
            unexpected.forEach {
                add("unexpected project on the resolved graph: $it")
            }
            forbiddenExternals.forEach {
                add("forbidden external dependency reached the sample: $it")
            }
            stale.forEach {
                add("allowlist entry no longer on the graph (remove it): $it")
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("isolationCheck FAILED")
                    appendLine("  $root (${configurationName.get()})")
                    problems.forEach { appendLine("    $it") }
                    appendLine("  allowlist: ${allowlistDisplayPath.get()}")
                },
            )
        }
    }

    private fun writeReport(
        root: String,
        actual: List<String>,
        unexpected: List<String>,
        stale: List<String>,
        forbiddenExternals: List<String>,
    ) {
        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("isolationCheck $root (${configurationName.get()})")
                appendLine("projects on the resolved graph: ${actual.size}")
                actual.forEach { appendLine("  $it") }
                appendLine("unexpected: ${unexpected.size}")
                unexpected.forEach { appendLine("  $it") }
                appendLine("stale allowlist entries: ${stale.size}")
                stale.forEach { appendLine("  $it") }
                appendLine("forbidden externals: ${forbiddenExternals.size}")
                forbiddenExternals.forEach { appendLine("  $it") }
            },
        )
    }
}
