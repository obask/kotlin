/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.gradle.api.DomainObjectCollection
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

internal fun KotlinMultiplatformExtension.applyKotlinTargetHierarchy(
    hierarchyDescriptor: KotlinTargetHierarchyDescriptor,
    targets: DomainObjectCollection<KotlinTarget>
) {
    targets
        .matching { target -> target.platformType != KotlinPlatformType.common }
        .all { target ->
            target.compilations.all { compilation ->
                hierarchyDescriptor.hierarchies(compilation).forEach { hierarchy ->
                    applyKotlinTargetHierarchy(hierarchy, compilation)
                }
            }
        }
}

internal fun KotlinMultiplatformExtension.applyKotlinTargetHierarchy(
    hierarchy: KotlinTargetHierarchy,
    compilation: KotlinCompilation<*>
): KotlinSourceSet {
    val sharedSourceSet = sourceSets.maybeCreate(lowerCamelCaseName(hierarchy.name, compilation.name))

    hierarchy.children
        .map { childHierarchy -> applyKotlinTargetHierarchy(childHierarchy, compilation) }
        .forEach { childSourceSet -> childSourceSet.dependsOn(sharedSourceSet) }

    if (hierarchy.children.isEmpty()) {
        compilation.defaultSourceSet.dependsOn(sharedSourceSet)
    }

    return sharedSourceSet
}
