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

internal fun KotlinMultiplatformExtension.withHierarchy(
    hierarchy: KotlinTargetHierarchy, configure: KotlinTargetContainerWithPresetFunctions.() -> Unit
) {
    KotlinTargetContainerWithHierarchy(this, hierarchy).configure()
}

private class KotlinTargetContainerWithHierarchy(
    private val extension: KotlinMultiplatformExtension,
    private val hierarchy: KotlinTargetHierarchy
) : KotlinTargetContainerWithPresetFunctions {

    override val presets: NamedDomainObjectCollection<KotlinTargetPreset<*>>
        get() = extension.presets

    override val targets: NamedDomainObjectCollection<KotlinTarget>
        get() = extension.targets

    override fun <T : KotlinTarget> configureOrCreate(
        targetName: String,
        targetPreset: KotlinTargetPreset<T>,
        configure: T.() -> Unit
    ): T {
        /* Target already created. Let's just delegate the call to configure it and return */
        if (targets.findByName(targetName) != null) {
            return super.configureOrCreate(targetName, targetPreset, configure)
        }

        /* New target created */
        val target = super.configureOrCreate(targetName, targetPreset, configure)
        applyHierarchy(target, hierarchy)
        return target
    }

    private fun applyHierarchy(target: KotlinTarget, node: KotlinTargetHierarchy) {
        if (!node.predicate(target)) return
        target.compilations.all { compilation -> applyHierarchy(compilation, node, null) }
    }

    private fun applyHierarchy(compilation: KotlinCompilation<*>, node: KotlinTargetHierarchy, parentNode: KotlinTargetHierarchy?) {
        /* Create source sets in the hierarchy and connect them */
        val sharedSourceSet = maybeCreateSourceSet(node, compilation)
        if (parentNode != null) sharedSourceSet.dependsOn(maybeCreateSourceSet(parentNode, compilation))

        val applicableChildren = node.children.filter { child -> child.predicate(compilation.target) }
        if (applicableChildren.isEmpty()) compilation.defaultSourceSet.dependsOn(sharedSourceSet)
        else applicableChildren.forEach { child -> applyHierarchy(compilation, child, node) }
    }

    private fun maybeCreateSourceSet(node: KotlinTargetHierarchy, compilation: KotlinCompilation<*>): KotlinSourceSet {
        return extension.sourceSets.maybeCreate(lowerCamelCaseName(node.name, compilation.name))
    }
}
