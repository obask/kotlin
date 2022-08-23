/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

interface KotlinTargetHierarchyDsl {
    fun set(hierarchyDescriptor: KotlinTargetHierarchyDescriptor)
    fun natural(describeExtension: (KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)? = null)
    fun custom(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)
}

internal class KotlinTargetHierarchyDslImpl(private val kotlin: KotlinMultiplatformExtension) : KotlinTargetHierarchyDsl {
    override fun set(hierarchyDescriptor: KotlinTargetHierarchyDescriptor) {
        apply(kotlin, hierarchyDescriptor)
    }

    override fun natural(describeExtension: (KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)?) {
        val hierarchy = if (describeExtension != null) naturalKotlinTargetHierarchy.extend(describeExtension)
        else naturalKotlinTargetHierarchy
        apply(kotlin, hierarchy)
    }

    override fun custom(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit) {
        apply(kotlin, KotlinTargetHierarchyDescriptor(describe))
    }
}

private fun apply(kotlin: KotlinMultiplatformExtension, hierarchyDescriptor: KotlinTargetHierarchyDescriptor) {
    /* Share one single container for creating 'virtual' SourceSets instead of physical once. */
    val virtualSourceSets = kotlin.project.project.objects.domainObjectContainer(VirtualKotlinSourceSet::class.java) { name ->
        VirtualKotlinSourceSetImpl(name, kotlin.sourceSets)
    }

    kotlin.targets.all { target ->
        if (target.platformType == KotlinPlatformType.common) return@all
        target.compilations.all { compilation -> hierarchyDescriptor.hierarchy(compilation).setup(virtualSourceSets, compilation) }
    }
}