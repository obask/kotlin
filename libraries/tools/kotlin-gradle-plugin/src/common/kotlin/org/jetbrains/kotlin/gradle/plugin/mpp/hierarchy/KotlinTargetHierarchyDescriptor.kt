/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@ExperimentalKotlinGradlePluginApi
interface KotlinTargetHierarchyDescriptor {
    fun hierarchy(target: KotlinTarget): KotlinTargetHierarchy
    fun extend(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit): KotlinTargetHierarchyDescriptor
}

@ExperimentalKotlinGradlePluginApi
fun KotlinTargetHierarchyDescriptor(
    describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit
): KotlinTargetHierarchyDescriptor {
    return KotlinTargetHierarchyDescriptorImpl(describe)
}

private class KotlinTargetHierarchyDescriptorImpl(
    private val describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit
) : KotlinTargetHierarchyDescriptor {
    override fun hierarchy(target: KotlinTarget): KotlinTargetHierarchy {
        val builder = KotlinTargetHierarchyBuilderImpl(KotlinTargetHierarchy.ROOT_NAME)
        builder.describe(target)
        return builder.build()
    }

    override fun extend(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit): KotlinTargetHierarchyDescriptor {
        val sourceDescribe = this.describe
        return KotlinTargetHierarchyDescriptor { target ->
            sourceDescribe(target)
            describe(target)
        }
    }
}
