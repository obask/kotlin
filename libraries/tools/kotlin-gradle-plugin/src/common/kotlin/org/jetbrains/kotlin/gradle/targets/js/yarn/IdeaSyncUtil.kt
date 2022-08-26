/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.yarn

import org.gradle.api.invocation.Gradle
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.VariantImplementationFactories
import org.jetbrains.kotlin.gradle.plugin.internal.DefaultIdeaSyncDetectorVariantFactory
import org.jetbrains.kotlin.gradle.plugin.internal.IdeaSyncDetector
import org.jetbrains.kotlin.gradle.plugin.internal.IdeaSyncDetectorG6

fun registerVariantImplementations(gradle: Gradle) {
    val factories = VariantImplementationFactories.get(gradle)

    if (GradleVersion.current() < GradleVersion.version("7.5")) {
        factories[IdeaSyncDetector.IdeaSyncDetectorVariantFactory::class] =
            IdeaSyncDetectorG6.IdeaSyncDetectorVariantFactoryG6()
    } else {
        factories[IdeaSyncDetector.IdeaSyncDetectorVariantFactory::class] =
            DefaultIdeaSyncDetectorVariantFactory()
    }
}