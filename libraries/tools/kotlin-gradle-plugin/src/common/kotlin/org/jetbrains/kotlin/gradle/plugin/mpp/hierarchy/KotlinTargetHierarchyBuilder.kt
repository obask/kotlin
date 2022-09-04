/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.tooling.core.closure

@ExperimentalKotlinGradlePluginApi
interface KotlinTargetHierarchyBuilder {
    val target: KotlinTarget
    val compilation: KotlinCompilation<*>
    fun group(name: String, build: KotlinTargetHierarchyBuilder.() -> Unit = {})

    val isNative: Boolean get() = target is KotlinNativeTarget
    val isApple: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family.isAppleFamily }
    val isIos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.IOS }
    val isWatchos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.WATCHOS }
    val isMacos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.OSX }
    val isTvos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.TVOS }
    val isWindows: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.MINGW }
    val isLinux: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.LINUX }
    val isAndroidNative: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.ANDROID }

    val isJvm: Boolean get() = target is KotlinJvmTarget
    val isAndroidJvm: Boolean get() = target is KotlinAndroidTarget
    val isJsLegacy: Boolean get() = target is KotlinJsTarget
    val isJs: Boolean get() = target is KotlinJsIrTarget
}

private typealias KotlinTargetHierarchies = Set<KotlinTargetHierarchy>

internal class KotlinTargetHierarchyBuilderImpl(
    override val compilation: KotlinCompilation<*>,
    private val allGroups: MutableMap<String, KotlinTargetHierarchyBuilderImpl> = mutableMapOf(),
) : KotlinTargetHierarchyBuilder {

    override val target: KotlinTarget = compilation.target

    private val groups = mutableMapOf<String, KotlinTargetHierarchyBuilderImpl>()

    override fun group(name: String, build: KotlinTargetHierarchyBuilder.() -> Unit) {
        groups.getOrPut(name) {
            allGroups.getOrPut(name) { KotlinTargetHierarchyBuilderImpl(compilation, allGroups) }
        }.also(build)
    }

    fun build(): Set<KotlinTargetHierarchy> {
        return build(mutableMapOf())
    }

    private fun build(cache: MutableMap<String /* name */, KotlinTargetHierarchy>): KotlinTargetHierarchies {
        val roots = groups.map { (name, builder) ->
            cache.getOrPut(name) { KotlinTargetHierarchy(name, builder.build(cache)) }
        }.toSet()

        /* Filter unnecessary roots that are already present in some other root */
        val childrenClosure = roots.flatMap { root -> root.closure { it.children } }.toSet()
        return roots - childrenClosure
    }
}
