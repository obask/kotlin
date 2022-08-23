/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

@ExperimentalKotlinGradlePluginApi
interface KotlinTargetHierarchyBuilder {
    val compilation: KotlinCompilation<*>
    fun group(name: String, build: KotlinTargetHierarchyBuilder.() -> Unit = {})
}

internal class KotlinTargetHierarchyBuilderImpl(
    private val name: String,
    override val compilation: KotlinCompilation<*>,
) : KotlinTargetHierarchyBuilder {

    private val children = mutableMapOf<String, KotlinTargetHierarchyBuilderImpl>()

    override fun group(name: String, build: KotlinTargetHierarchyBuilder.() -> Unit) {
        children.getOrPut(name) { KotlinTargetHierarchyBuilderImpl(name, compilation) }.also(build)
    }

    fun build(): KotlinTargetHierarchy = KotlinTargetHierarchy(name, children.values.map { it.build() }.toSet())
}
