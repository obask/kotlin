/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

internal val naturalKotlinTargetHierarchy = KotlinTargetHierarchy("common") {
    child("native", { target -> target is KotlinNativeTarget }) {
        child("apple", { target -> target.konanTarget?.family?.isAppleFamily == true }) {
            child("macos", { target -> target.konanTarget?.family == Family.OSX })
            child("ios", { target -> target.konanTarget?.family == Family.IOS })
            child("watchos", { target -> target.konanTarget?.family == Family.WATCHOS })
            child("tvos", { target -> target.konanTarget?.family == Family.TVOS })
        }

        child("linux", { target -> target.konanTarget?.family == Family.LINUX })
        child("windows", { target -> target.konanTarget?.family == Family.MINGW })
        child("androidNative", { target -> target.konanTarget?.family == Family.ANDROID })
    }
}

private val KotlinTarget.konanTarget: KonanTarget? get() = if (this is KotlinNativeTarget) konanTarget else null