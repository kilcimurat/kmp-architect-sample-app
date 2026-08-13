package com.mkilci.kmparchitect.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The single place the supported target set is declared. Every KMP library in the repository gets
 * Android + iosArm64 + iosSimulatorArm64 and nothing else — adding a target here is a deliberate,
 * reviewable change rather than something that drifts per module.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()

            // The `android` block inside `kotlin { }` is a KotlinMultiplatformAndroidLibraryTarget,
            // not the bare extension interface — only the target carries `compilerOptions`.
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                namespace = ProjectNaming.namespaceFor(path)
                compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
                minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()

                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
                withHostTest { }
            }

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.findLibrary("kotlin-test").get())
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
