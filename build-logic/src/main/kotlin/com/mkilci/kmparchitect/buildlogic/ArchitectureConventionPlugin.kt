package com.mkilci.kmparchitect.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
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
            infrastructureModules.set(target.layout.projectDirectory.file("config/infrastructure-modules.txt"))
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
            val externalEdges = mutableListOf<String>()
            val sourceRoots = mutableListOf<String>()
            val sourceDirs = mutableListOf<File>()

            target.allprojects.forEach { project ->
                if (project == target) return@forEach

                project.configurations.forEach { configuration ->
                    // KMP synthesizes this lock-file metadata bucket from other projects' source
                    // sets. Its contents are not dependencies declared by this project and treating
                    // them as direct edges produces false app -> test-fixtures violations.
                    if (configuration.name == "swiftPMDependenciesForLockFilesMetadataClasspathDependencies") {
                        return@forEach
                    }
                    configuration.dependencies
                        .filterIsInstance<ProjectDependency>()
                        .forEach { dependency ->
                            edges += "${project.path}|${dependency.path}|${configuration.name}"
                        }
                    configuration.dependencies
                        .filterIsInstance<ExternalModuleDependency>()
                        .forEach { dependency ->
                            val group = dependency.group ?: return@forEach
                            externalEdges += "${project.path}|$group:${dependency.name}|${configuration.name}"
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
                this.externalEdges.set(externalEdges.distinct().sorted())
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
        // A sample is isolated on both platforms or on neither. The Android graph is resolved from
        // the executable that a developer installs; the iOS graph from the framework module that
        // Xcode embeds. Same allowlist, two roots.
        sampleModules(root, leaf = ANDROID_HOST).forEach { (feature, project) ->
            registerIsolationCheck(root, aggregate, feature, project, ANDROID, ANDROID_CONFIGURATION)
        }
        sampleModules(root, leaf = IOS_FRAMEWORK).forEach { (feature, project) ->
            registerIsolationCheck(root, aggregate, feature, project, IOS, IOS_CONFIGURATION)
        }
    }

    private fun sampleModules(root: Project, leaf: String): List<Pair<String, Project>> =
        root.allprojects.mapNotNull { project ->
            val segments = project.path.split(":").filter(String::isNotBlank)
            if (segments.size == 3 && segments[0] == "sample" && segments[2] == leaf) {
                segments[1] to project
            } else {
                null
            }
        }

    private fun registerIsolationCheck(
        root: Project,
        aggregate: TaskProvider<Task>,
        feature: String,
        sample: Project,
        platform: String,
        configurationName: String,
    ) {
        // A missing configuration must not silently drop the gate: a check that quietly stops
        // running is worse than no check, because the report still says the sample is isolated.
        val configuration = sample.configurations.findByName(configurationName)
            ?: error(
                "isolationCheck cannot resolve '$configurationName' on ${sample.path}. " +
                    "The $platform gate would silently stop running; fix the configuration name.",
            )

        val taskName = "isolationCheck" +
            feature.replaceFirstChar { it.uppercaseChar() } +
            platform.replaceFirstChar { it.uppercaseChar() }

        val task = root.tasks.register<IsolationCheckTask>(taskName) {
            group = "verification"
            description = "Checks the resolved $platform graph of ${sample.path} against its allowlist."
            samplePath.set(sample.path)
            this.configurationName.set(configurationName)
            this.platform.set(platform)
            allowlistFile.set(root.layout.projectDirectory.file("sample/$feature/isolation-allowlist.txt"))
            allowlistDisplayPath.set("sample/$feature/isolation-allowlist.txt")
            forbiddenModulePrefixes.set(forbiddenSampleModulePrefixes)
            resolvedProjects.set(configuration.projectNodes())
            resolvedModules.set(configuration.moduleNodes())
            report.set(root.layout.buildDirectory.file("reports/architecture/isolation-$feature-$platform.txt"))
        }
        aggregate.configure { dependsOn(task) }
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
        const val ANDROID = "android"
        const val IOS = "ios"

        const val ANDROID_HOST = "androidApp"
        const val IOS_FRAMEWORK = "shared"

        /** The daily-loop artifact: what a feature developer actually installs. */
        const val ANDROID_CONFIGURATION = "debugRuntimeClasspath"

        /**
         * What actually compiles into the static framework Xcode embeds.
         *
         * Only the simulator target is resolved: both iOS targets are fed by the same `iosMain`
         * source set here, so `iosArm64` resolves the same projects and a second task would be
         * duplication. Add it the day a target-specific source set appears.
         */
        const val IOS_CONFIGURATION = "iosSimulatorArm64CompileKlibraries"
    }
}
