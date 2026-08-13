package com.mkilci.kmparchitect.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Registers the two verification entry points on the root project.
 *
 * They are separate tasks on purpose. `architectureCheck` reads declared metadata and sources and is
 * fast; `isolationCheck` resolves real classpaths and is slower. Keeping them apart means a
 * contributor who widened a sample by one project sees an isolation failure naming that project,
 * not a generic architecture error.
 */
class ArchitectureConventionPlugin : Plugin<Project> {

    /**
     * The data stack. Its absence from a sample's graph is where the build-time saving comes from,
     * so it is asserted rather than assumed.
     *
     * `kotlinx-serialization-core` is deliberately *not* here: typed navigation routes are
     * `@Serializable`, so the core runtime legitimately reaches every sample. Only the JSON parser,
     * which exists to turn DTOs into models, indicates that the data layer leaked in. The first run
     * of this check flagged `serialization-core` and this rule — not the code — was what was wrong.
     */
    private val forbiddenSampleModulePrefixes = setOf(
        "io.ktor:",
        "app.cash.sqldelight:",
        "org.jetbrains.kotlinx:kotlinx-serialization-json",
    )

    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "kmpa.architecture applies to the root project only"
        }

        val architectureCheck = target.tasks.register<ArchitectureCheckTask>("architectureCheck") {
            group = "verification"
            description = "Checks declared dependency edges and Kotlin sources against the architecture rules."
            apiAllowlist.set(target.layout.projectDirectory.file("config/api-allowlist.txt"))
            report.set(target.layout.buildDirectory.file("reports/architecture/architecture-check.txt"))
        }

        val isolationCheck = target.tasks.register("isolationCheck") {
            group = "verification"
            description = "Verifies every sample's resolved graph against its isolation allowlist."
        }

        // Everything below reads other projects' models, so it runs once they are all configured and
        // stores only immutable strings on the tasks.
        target.gradle.projectsEvaluated {
            val edges = mutableListOf<String>()
            val sourceRoots = mutableListOf<String>()
            val sourceDirs = mutableListOf<File>()

            target.allprojects.forEach { project ->
                if (project == target) return@forEach

                project.configurations.forEach { configuration ->
                    configuration.dependencies
                        .filterIsInstance<ProjectDependency>()
                        .forEach { dependency ->
                            edges += "${project.path}|${dependency.path}|${configuration.name}"
                        }
                }

                val srcDir = project.layout.projectDirectory.dir("src").asFile
                if (srcDir.exists()) {
                    sourceRoots += "${project.path}|${srcDir.absolutePath}"
                    sourceDirs += srcDir
                }
            }

            architectureCheck.configure {
                this.edges.set(edges.distinct().sorted())
                this.sourceRoots.set(sourceRoots.sorted())
                this.sourceFiles.setFrom(sourceDirs)
            }

            registerSampleIsolationTasks(target, isolationCheck)
        }
    }

    private fun registerSampleIsolationTasks(
        root: Project,
        aggregate: TaskProvider<Task>,
    ) {
        val sampleHosts = root.allprojects.filter { project ->
            val segments = project.path.split(":").filter(String::isNotBlank)
            segments.size == 3 && segments[0] == "sample" && segments[2] == "androidApp"
        }

        sampleHosts.forEach { host ->
            val feature = host.path.split(":")[2]
            val allowlist = root.layout.projectDirectory.file("sample/$feature/isolation-allowlist.txt")
            val configuration = host.configurations.findByName(RESOLVED_CONFIGURATION) ?: return@forEach

            val task = root.tasks.register<IsolationCheckTask>("isolationCheck${feature.replaceFirstChar { it.uppercaseChar() }}") {
                group = "verification"
                description = "Checks the resolved graph of ${host.path} against its allowlist."
                samplePath.set(host.path)
                configurationName.set(RESOLVED_CONFIGURATION)
                allowlistFile.set(allowlist)
                allowlistDisplayPath.set("sample/$feature/isolation-allowlist.txt")
                forbiddenModulePrefixes.set(forbiddenSampleModulePrefixes)
                resolvedProjects.set(configuration.projectNodes())
                resolvedModules.set(configuration.moduleNodes())
                report.set(root.layout.buildDirectory.file("reports/architecture/isolation-$feature.txt"))
            }
            aggregate.configure { dependsOn(task) }
        }
    }

    private fun Configuration.projectNodes() =
        incoming.resolutionResult.rootComponent.map { root ->
            buildSet<String> { walk(root, mutableSetOf()) { component ->
                (component.id as? ProjectComponentIdentifier)?.let { add(it.projectPath) }
            } }
        }

    private fun Configuration.moduleNodes() =
        incoming.resolutionResult.rootComponent.map { root ->
            buildSet<String> { walk(root, mutableSetOf()) { component ->
                (component.id as? ModuleComponentIdentifier)?.let { add("${it.group}:${it.module}") }
            } }
        }

    private fun walk(
        component: ResolvedComponentResult,
        seen: MutableSet<Any>,
        visit: (ResolvedComponentResult) -> Unit,
    ) {
        if (!seen.add(component.id)) return
        visit(component)
        component.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .forEach { walk(it.selected, seen, visit) }
    }

    private companion object {
        /** The daily-loop artifact: what a feature developer actually installs. */
        const val RESOLVED_CONFIGURATION = "debugRuntimeClasspath"
    }
}
