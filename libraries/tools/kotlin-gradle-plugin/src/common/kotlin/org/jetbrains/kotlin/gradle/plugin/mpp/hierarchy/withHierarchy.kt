/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.gradle.api.NamedDomainObjectCollection
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

@ExperimentalKotlinGradlePluginApi
fun KotlinMultiplatformExtension.withHierarchy(
    hierarchy: KotlinTargetHierarchyDescriptor, configure: KotlinTargetContainerWithHierarchy.() -> Unit
) {
    KotlinTargetContainerWithHierarchyImpl(this, hierarchy).apply(configure).setup()
}

@ExperimentalKotlinGradlePluginApi
interface KotlinTargetContainerWithHierarchy : KotlinTargetContainerWithPresetFunctions {
    /**
     * Possibility to extend the current [KotlinTargetHierarchy] and add additional groups.
     * It is possible to extend already defined 'groups' by re-declaring them.
     *
     * ## Examples:
     *
     * ### Sharing Code between linux and macos (posix)
     * ```kotlin
     * extendHierarchy { target ->
     *       if (target is KotlinNativeTarget) {
     *           group("native") {
     *               if (target.konanTarget.family.isAppleFamily || target.konanTarget.family == LINUX) {
     *                   group("posix")
     *               }
     *           }
     *       }
     *   }
     * ```
     *
     * ### Sharing code between native and jvm targets
     * ```kotlin
     * extendHierarchy { target ->
     *     if (target is KotlinJvmTarget || target is KotlinNativeTarget ) {
     *         group("jvmAndNative")
     *    }
     * }
     * ```
     */
    fun extendHierarchy(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)
}

private class KotlinTargetContainerWithHierarchyImpl(
    private val extension: KotlinMultiplatformExtension,
    private var descriptor: KotlinTargetHierarchyDescriptor
) : KotlinTargetContainerWithHierarchy {

    override val presets: NamedDomainObjectCollection<KotlinTargetPreset<*>>
        get() = extension.presets

    override val targets: NamedDomainObjectCollection<KotlinTarget>
        get() = extension.targets

    private val configuredTargets = mutableSetOf<KotlinTarget>()

    override fun extendHierarchy(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit) {
        descriptor = descriptor.extend(describe)
    }

    override fun <T : KotlinTarget> configureOrCreate(
        targetName: String,
        targetPreset: KotlinTargetPreset<T>,
        configure: T.() -> Unit
    ): T {
        val target = super.configureOrCreate(targetName, targetPreset, configure)
        configuredTargets += target
        return target
    }

    fun setup() {
        configuredTargets.forEach { target -> applyHierarchy(descriptor.hierarchy(target), target) }
    }

    private fun applyHierarchy(hierarchy: KotlinTargetHierarchy, target: KotlinTarget) {
        target.compilations.all { compilation ->
            applyHierarchy(hierarchy, compilation)
        }
    }

    private fun applyHierarchy(hierarchy: KotlinTargetHierarchy, compilation: KotlinCompilation<*>): KotlinSourceSet? {
        val childSourceSets = if (hierarchy.children.isNotEmpty())
            hierarchy.children.mapNotNull { child -> applyHierarchy(child, compilation) }
        else setOf(compilation.defaultSourceSet)

        val sharedSourceSet = extension.sourceSets.maybeCreate(lowerCamelCaseName(hierarchy.name, compilation.name))
        childSourceSets.forEach { childSourceSet -> childSourceSet.dependsOn(sharedSourceSet) }

        return sharedSourceSet
    }
}
