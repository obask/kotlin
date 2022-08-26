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
    val compilation: KotlinCompilation<*>
    fun group(name: String, build: KotlinTargetHierarchyBuilder.() -> Unit = {})

    val KotlinTarget.isNative: Boolean get() = this is KotlinNativeTarget
    val KotlinTarget.isApple: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family.isAppleFamily

    val KotlinTarget.isIos: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.IOS
    val KotlinTarget.isWatchos: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.WATCHOS
    val KotlinTarget.isMacos: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.OSX
    val KotlinTarget.isTvos: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.TVOS
    val KotlinTarget.isWindows: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.MINGW
    val KotlinTarget.isLinux: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.LINUX
    val KotlinTarget.isAndroidNative: Boolean get() = this is KotlinNativeTarget && this.konanTarget.family == Family.ANDROID

    val KotlinTarget.isJvm: Boolean get() = this is KotlinJvmTarget
    val KotlinTarget.isAndroidJvm: Boolean get() = this is KotlinAndroidTarget
    val KotlinTarget.isJsLegacy: Boolean get() = this is KotlinJsTarget
    val KotlinTarget.isJs: Boolean get() = this is KotlinJsIrTarget
}

private typealias KotlinTargetHierarchies = Set<KotlinTargetHierarchy>

internal class KotlinTargetHierarchyBuilderImpl(
    override val compilation: KotlinCompilation<*>,
    private val allGroups: MutableMap<String, KotlinTargetHierarchyBuilderImpl> = mutableMapOf(),
) : KotlinTargetHierarchyBuilder {

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
