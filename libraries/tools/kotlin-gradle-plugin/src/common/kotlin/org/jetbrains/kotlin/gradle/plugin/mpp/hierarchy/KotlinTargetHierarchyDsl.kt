/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

interface KotlinTargetHierarchyDsl {
    fun set(hierarchyDescriptor: KotlinTargetHierarchyDescriptor)

    /**
     * Set's up a 'natural'/'default' hierarchy withing [KotlinTarget]'s in the project.
     *
     * #### Example 1
     *
     * ```kotlin
     * kotlin {
     *     hierarchy.default() // <- position of this call is not relevant!
     *
     *     iosX64()
     *     iosArm64()
     *     linuxX64()
     *     linuxArm64()
     * }
     * ```
     *
     * Will create the following SourceSets:
     * `[iosMain, iosTest, appleMain, appleTest, linuxMain, linuxTest, nativeMain, nativeTest]
     *
     *
     * Hierarchy:
     * ```
     *                                                                     common
     *                                                                        |
     *                                                      +-----------------+-------------------+
     *                                                      |                                     |
     *
     *                                                    native                                 ...
     *
     *                                                     |
     *                                                     |
     *                                                     |
     *         +----------------------+--------------------+-----------------------+
     *         |                      |                    |                       |
     *
     *       apple                  linux              windows              androidNative
     *
     *         |
     *  +-----------+------------+------------+
     *  |           |            |            |
     *
     * macos       ios         tvos        watchos
     * ```
     *
     * #### Example 2: Adding custom groups
     * Let's imagine we would additionally like to share code between linux and apple (unixLike)
     *
     * ```kotlin
     * kotlin {
     *     hierarchy.default { target ->
     *         if(target.isNative) {
     *             group("native") { // <- we can re-declare already existing groups and connect children to it!
     *                 if(target.isLinux || target.isApple) {
     *                     group("unixLike")
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param describeExtension: Additional groups can  be described to extend the 'default'/'natural' hierarchy:
     * @see KotlinTargetHierarchyDescriptor.extend
     */
    fun default(describeExtension: (KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)? = null)
    fun custom(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)
}

internal class KotlinTargetHierarchyDslImpl(private val kotlin: KotlinMultiplatformExtension) : KotlinTargetHierarchyDsl {
    override fun set(hierarchyDescriptor: KotlinTargetHierarchyDescriptor) {
        kotlin.applyKotlinTargetHierarchy(hierarchyDescriptor, kotlin.targets)
    }

    override fun default(describeExtension: (KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit)?) {
        val hierarchyDescriptor =
            if (describeExtension != null) naturalKotlinTargetHierarchy.extend(describeExtension)
            else naturalKotlinTargetHierarchy
        kotlin.applyKotlinTargetHierarchy(hierarchyDescriptor, kotlin.targets)
    }

    override fun custom(describe: KotlinTargetHierarchyBuilder.(target: KotlinTarget) -> Unit) {
        kotlin.applyKotlinTargetHierarchy(KotlinTargetHierarchyDescriptor(describe), kotlin.targets)
    }
}
