/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.cocoapods

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_FRAMEWORK_PREFIX
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.native.cocoapods.CocoapodsPodspecExtension
import java.io.File
import javax.inject.Inject

abstract class CocoapodsExtension @Inject constructor(private val project: Project) : CocoapodsPodspecExtension(project) {

    /**
     * Configure existing file `Podfile`.
     */
    var podfile: File? = null

    internal var needPodspec: Boolean = true

    /**
     * Setup plugin not to produce podspec file for cocoapods section
     */
    fun noPodspec() {
        needPodspec = false
    }

    /**
     * Setup plugin to generate synthetic xcodeproj compatible with static libraries
     */
    fun useLibraries() {
        useLibraries = true
    }

    internal var useLibraries: Boolean = false

    /**
     * Configure framework of the pod built from this project.
     */
    fun framework(configure: Framework.() -> Unit) {
        forAllPodFrameworks(configure)
    }

    /**
     * Configure framework of the pod built from this project.
     */
    fun framework(configure: Action<Framework>) {
        forAllPodFrameworks(configure)
    }

    private val anyPodFramework = project.provider {
        val anyTarget = project.multiplatformExtension.supportedTargets().first()
        val anyFramework = anyTarget.binaries
            .matching { it.name.startsWith(POD_FRAMEWORK_PREFIX) }
            .withType(Framework::class.java)
            .first()
        anyFramework
    }

    internal val podFrameworkName = anyPodFramework.map { it.baseName }
    internal val podFrameworkIsStatic = anyPodFramework.map { it.isStatic }

    /**
     * Configure custom Xcode Configurations to Native Build Types mapping
     */
    val xcodeConfigurationToNativeBuildType: MutableMap<String, NativeBuildType> = mutableMapOf(
        "Debug" to NativeBuildType.DEBUG,
        "Release" to NativeBuildType.RELEASE
    )

    /**
     * Configure output directory for pod publishing
     */
    var publishDir: File = CocoapodsBuildDirs(project).publish

    internal val specRepos = SpecRepos()


    /**
     * Add spec repositories (note that spec repository is different from usual git repository).
     * Please refer to <a href="https://guides.cocoapods.org/making/private-cocoapods.html">cocoapods documentation</a>
     * for additional information.
     * Default sources (cdn.cocoapods.org) implicitly included.
     */
    fun specRepos(configure: SpecRepos.() -> Unit) = specRepos.configure()

    /**
     * Add spec repositories (note that spec repository is different from usual git repository).
     * Please refer to <a href="https://guides.cocoapods.org/making/private-cocoapods.html">cocoapods documentation</a>
     * for additional information.
     * Default sources (cdn.cocoapods.org) implicitly included.
     */
    fun specRepos(configure: Action<SpecRepos>) = specRepos {
        configure.execute(this)
    }

    private fun forAllPodFrameworks(action: Action<in Framework>) {
        project.multiplatformExtension.supportedTargets().all { target ->
            target.binaries
                .matching { it.name.startsWith(POD_FRAMEWORK_PREFIX) }
                .withType(Framework::class.java) { action.execute(it) }
        }
    }

    class SpecRepos {
        @get:Internal
        internal val specRepos = mutableSetOf("https://cdn.cocoapods.org")

        fun url(url: String) {
            specRepos.add(url)
        }

        @Input
        internal fun getAll(): Collection<String> {
            return specRepos
        }
    }
}

