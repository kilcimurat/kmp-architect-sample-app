package com.mkilci.kmparchitect.buildlogic

import com.mkilci.kmparchitect.buildlogic.rules.IsolationRules
import com.mkilci.kmparchitect.buildlogic.rules.IsolationVerdict
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
 *
 * One instance runs per sample **per platform**: the Android graph comes from the executable's
 * runtime classpath, the iOS graph from the framework module's klib compile classpath. Checking only
 * one platform would leave the other free to pull in a data module — a static framework links whatever
 * reaches it just as happily as an APK packages it.
 */
abstract class IsolationCheckTask : DefaultTask() {

    @get:Input
    abstract val samplePath: Property<String>

    @get:Input
    abstract val configurationName: Property<String>

    /** Which platform's graph this instance resolves, for reports and failure messages. */
    @get:Input
    abstract val platform: Property<String>

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
        val root = samplePath.get()
        val verdict = IsolationRules.evaluate(
            rootPath = root,
            resolvedProjects = resolvedProjects.get(),
            resolvedModules = resolvedModules.get(),
            allowlist = IsolationRules.parseAllowlist(allowlistFile.get().asFile.readLines()),
            forbiddenModulePrefixes = forbiddenModulePrefixes.get(),
        )

        writeReport(root, verdict)

        if (verdict.problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("isolationCheck FAILED")
                    appendLine("  ${platform.get()}: $root (${configurationName.get()})")
                    verdict.problems.forEach { appendLine("    $it") }
                    appendLine("  allowlist: ${allowlistDisplayPath.get()}")
                },
            )
        }
    }

    private fun writeReport(root: String, verdict: IsolationVerdict) {
        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("isolationCheck ${platform.get()} $root (${configurationName.get()})")
                appendLine("projects on the resolved graph: ${verdict.actual.size}")
                verdict.actual.forEach { appendLine("  $it") }
                appendLine("unexpected: ${verdict.unexpected.size}")
                verdict.unexpected.forEach { appendLine("  $it") }
                appendLine("stale allowlist entries: ${verdict.stale.size}")
                verdict.stale.forEach { appendLine("  $it") }
                appendLine("forbidden externals: ${verdict.forbiddenExternals.size}")
                verdict.forbiddenExternals.forEach { appendLine("  $it") }
            },
        )
    }
}
