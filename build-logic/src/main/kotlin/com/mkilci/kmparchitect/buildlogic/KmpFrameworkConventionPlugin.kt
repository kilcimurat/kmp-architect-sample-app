package com.mkilci.kmparchitect.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Compose convention plus a static Apple framework, for modules an Xcode host embeds: the
 * application root and every feature sample.
 *
 * The base name is derived from the Gradle path rather than `project.name`, because several
 * projects are named `shared` and duplicate framework names fail confusingly inside Xcode. Override
 * with the `frameworkBaseName` Gradle property when a host needs a specific product name.
 */
class KmpFrameworkConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("kmpa.kmp.compose")

        val derivedName = ProjectNaming.frameworkBaseNameFor(path)
        val configuredName = providers.gradleProperty("frameworkBaseName").orElse(derivedName)

        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinNativeTarget>().configureEach {
                binaries.framework {
                    baseName = configuredName.get()
                    isStatic = true
                }
            }
        }
    }
}
