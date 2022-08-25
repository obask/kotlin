/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.mpp.isMain
import org.jetbrains.kotlin.gradle.plugin.mpp.isTest

internal val naturalKotlinTargetHierarchy = KotlinTargetHierarchyDescriptor hierarchy@{ target ->
    if (!compilation.isMain() && !compilation.isTest()) {
        /* This hierarchy is only defined for default 'main' and 'test' compilations */
        return@hierarchy
    }

    if (target.isNative) {
        group("native") {
            if (target.isApple) {
                group("apple") {
                    if (target.isIos) group("ios")
                    if (target.isTvos) group("tvos")
                    if (target.isWatchos) group("watchos")
                    if (target.isMacos) group("macos")
                }
            }

            if (target.isLinux) group("linux")
            if (target.isWindows) group("windows")
            if (target.isAndroidNative) group("androidNative")
        }
    }
}
