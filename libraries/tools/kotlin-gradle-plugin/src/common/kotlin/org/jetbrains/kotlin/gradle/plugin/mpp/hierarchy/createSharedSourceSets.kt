/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.utils.getOrCreate
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

@ExperimentalKotlinGradlePluginApi
fun KotlinMultiplatformExtension.createSharedSourceSets(
    sourceSetPrefix: String, vararg targets: KotlinTarget, configure: KotlinSourceSet.() -> Unit = {}
) {
    targets.forEach { target ->
        target.compilations.all { compilation ->

            val sharedSourceSet = sourceSets.getOrCreate(
                lowerCamelCaseName(sourceSetPrefix, compilation.name), invokeWhenCreated = configure
            )

            if (compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME) {
                sharedSourceSet.dependsOn(sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME))
            }

            if (compilation.name == KotlinCompilation.TEST_COMPILATION_NAME) {
                sharedSourceSet.dependsOn(sourceSets.getByName(KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME))
            }

            compilation.defaultSourceSet.dependsOn(sharedSourceSet)
        }
    }
}