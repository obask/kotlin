/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.ExperimentalKotlinGradlePluginApi

@ExperimentalKotlinGradlePluginApi
data class KotlinTargetHierarchy(
    val name: String,
    val children: Set<KotlinTargetHierarchy>
) {
    companion object {
        const val ROOT_NAME = "common"
    }

    override fun toString(): String {
        if (children.isEmpty()) return name
        return name + "\n" + children.joinToString("\n").prependIndent("----")
    }
}