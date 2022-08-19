/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal interface KotlinTargetHierarchy {
    val name: String
    val predicate: (KotlinTarget) -> Boolean
    val children: List<KotlinTargetHierarchy>
}

internal class KotlinTargetHierarchyBuilder(private val name: String, private val predicate: (KotlinTarget) -> Boolean) {
    private val children = mutableListOf<KotlinTargetHierarchyBuilder>()

    fun child(name: String, predicate: (KotlinTarget) -> Boolean, builder: KotlinTargetHierarchyBuilder.() -> Unit = {}) {
        children += KotlinTargetHierarchyBuilder(name, predicate).also(builder)
    }

    fun build(): KotlinTargetHierarchy = KotlinTargetHierarchyImpl(
        name, predicate, children.map { it.build() }
    )
}

internal fun KotlinTargetHierarchy(
    rootName: String, rootPredicate: (KotlinTarget) -> Boolean = { true }, builder: KotlinTargetHierarchyBuilder.() -> Unit
): KotlinTargetHierarchy {
    return KotlinTargetHierarchyBuilder(rootName, rootPredicate).apply(builder).build()
}


private class KotlinTargetHierarchyImpl(
    override val name: String,
    override val predicate: (KotlinTarget) -> Boolean,
    override val children: List<KotlinTargetHierarchy> = emptyList()
) : KotlinTargetHierarchy
