/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

internal val naturalKotlinTargetHierarchy = KotlinTargetHierarchyDescriptor { target: KotlinTarget ->
    if (target is KotlinNativeTarget) {
        group("native") {
            val family = target.konanTarget.family
            if (family.isAppleFamily) {
                group("apple") {
                    if (family == Family.IOS) group("ios")
                    if (family == Family.TVOS) group("tvos")
                    if (family == Family.WATCHOS) group("watchos")
                    if (family == Family.OSX) group("macos")
                }
            }

            if (family == Family.LINUX) group("linux")
            if (family == Family.MINGW) group("windows")
            if (family == Family.ANDROID) group("androidNative")
        }
    }
}
