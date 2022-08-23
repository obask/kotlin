/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.dsl.kotlinExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.isMain
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

internal fun KotlinTargetHierarchyDescriptor.setup(targets: Set<KotlinTarget>) {
    if (targets.isEmpty()) return
    val project = targets.first().project
    val kotlinExtension = project.kotlinExtensionOrNull ?: return

    /* Share one single container for creating 'virtual' SourceSets instead of physical once. */
    val virtualSourceSets = project.project.objects.domainObjectContainer(VirtualKotlinSourceSet::class.java) { name ->
        VirtualKotlinSourceSetImpl(name, kotlinExtension.sourceSets)
    }

    targets.forEach { target ->
        target.compilations.all { compilation ->
            hierarchy(compilation).setup(virtualSourceSets, compilation)
        }
    }
}

internal fun KotlinTargetHierarchy.setup(
    virtualSourceSets: NamedDomainObjectContainer<VirtualKotlinSourceSet>, compilation: KotlinCompilation<*>
): VirtualKotlinSourceSet {
    val virtualSharedSourceSet = virtualSourceSets.maybeCreate(lowerCamelCaseName(name, compilation.name))

    children
        .map { childHierarchy -> childHierarchy.setup(virtualSourceSets, compilation) }.toSet()
        .forEach { virtualChildSourceSet -> virtualSharedSourceSet.pushChild(virtualChildSourceSet) }

    if (children.isEmpty()) {
        virtualSharedSourceSet.pushChild(compilation.defaultSourceSet)
    }

    return virtualSharedSourceSet
}

/**
 * Abstraction over regular KotlinSourceSets that allows building hierarchy of SourceSets
 * without actually allocating 'unnecessary' SourceSets.
 *
 * Shared SourceSets are only created 'on demand' (when actually more than one other SourceSet) depends on it.
 *
 */
internal interface VirtualKotlinSourceSet : Named {
    fun pushChild(sourceSet: KotlinSourceSet)
    fun pushChild(sourceSet: VirtualKotlinSourceSet)
    fun forceCreation()
    fun invokeWhenCreated(action: (KotlinSourceSet) -> Unit)
}

/* Implementation of VirtualSourceSet */

internal class VirtualKotlinSourceSetImpl(
    private val sourceSetName: String,
    private val sourceSets: NamedDomainObjectContainer<KotlinSourceSet>
) : VirtualKotlinSourceSet {

    override fun getName(): String = sourceSetName

    private val invokeWhenCreatedListeners = mutableListOf<(KotlinSourceSet) -> Unit>()

    private var createdSourceSet: KotlinSourceSet? = sourceSets.findByName(sourceSetName)

    private val children = mutableSetOf<KotlinSourceSet>()

    override fun pushChild(sourceSet: KotlinSourceSet) {
        if (sourceSet.name == sourceSetName) return
        children.add(sourceSet)
        invokeWhenCreated { createdSourceSet -> sourceSet.dependsOn(createdSourceSet) }
        createIfNecessary()
    }

    override fun pushChild(sourceSet: VirtualKotlinSourceSet) {
        if (sourceSet.name == sourceSetName) return
        sourceSet.invokeWhenCreated { childSourceSet -> pushChild(childSourceSet) }
    }

    override fun forceCreation() {
        if (createdSourceSet != null) return
        val sourceSet = sourceSets.maybeCreate(sourceSetName)
        createdSourceSet = sourceSet
        invokeWhenCreatedListeners.toList().forEach { listener -> listener.invoke(sourceSet) }
        invokeWhenCreatedListeners.clear()
    }

    override fun invokeWhenCreated(action: (KotlinSourceSet) -> Unit) {
        val sourceSet = createdSourceSet
        if (sourceSet != null) {
            return action(sourceSet)
        }

        invokeWhenCreatedListeners.add(action)
    }

    private fun createIfNecessary() {
        /* Only create this source set, when there are at least two children */
        //if (children.size < 2) return //  TODO NOW: Enable this line  for 'sparse' mode
        forceCreation()
    }
}
