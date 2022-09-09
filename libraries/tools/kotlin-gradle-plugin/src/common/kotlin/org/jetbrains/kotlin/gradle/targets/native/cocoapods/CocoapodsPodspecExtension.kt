/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.cocoapods

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeXCFrameworkConfig
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin
import org.jetbrains.kotlin.gradle.plugin.cocoapods.asValidFrameworkName
import org.jetbrains.kotlin.gradle.targets.native.cocoapods.CocoapodsPodspecExtension.CocoapodsDependency.PodLocation.*
import org.jetbrains.kotlin.gradle.plugin.findExtension
import java.io.File
import java.net.URI
import javax.inject.Inject

//TODO is inheritance ok in this case?
abstract class CocoapodsPodspecExtension @Inject constructor(private val project: Project) {
    /**
     * Configure version of the pod
     */
    var version: String? = null

    /**
     * Configure authors of the pod built from this project.
     */
    var authors: String? = null

    /**
     * Configure name of the pod built from this project.
     */
    var name: String = project.name.asValidFrameworkName()

    /**
     * Configure license of the pod built from this project.
     */
    var license: String? = null

    /**
     * Configure description of the pod built from this project.
     */
    var summary: String? = null

    /**
     * Configure homepage of the pod built from this project.
     */
    var homepage: String? = null

    /**
     * Configure location of the pod built from this project.
     */
    var source: String? = null

    /**
     * Configure other podspec attributes
     */
    var extraSpecAttributes: MutableMap<String, String> = mutableMapOf()

    val ios: PodspecPlatformSettings = PodspecPlatformSettings("ios")

    val osx: PodspecPlatformSettings = PodspecPlatformSettings("osx")

    val tvos: PodspecPlatformSettings = PodspecPlatformSettings("tvos")

    val watchos: PodspecPlatformSettings = PodspecPlatformSettings("watchos")

    private val _pods = project.container(CocoapodsDependency::class.java)

    val podsAsTaskInput: List<CocoapodsDependency>
        get() = _pods.toList()

    /**
     * Returns a list of pod dependencies.
     */
    val pods: NamedDomainObjectSet<CocoapodsDependency>
        get() = _pods

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    @JvmOverloads
    fun pod(name: String, version: String? = null, path: File? = null, moduleName: String = name.asModuleName(), headers: String? = null) {
        // Empty string will lead to an attempt to create two podDownload tasks.
        // One is original podDownload and second is podDownload + pod.name
        require(name.isNotEmpty()) { "Please provide not empty pod name to avoid ambiguity" }
        var podSource = path
        if (path != null && !path.isDirectory) {
            val pattern = "\\W*pod(.*\"${name}\".*)".toRegex()
            val buildScript = project.buildFile
            val lines = buildScript.readLines()
            val lineNumber = lines.indexOfFirst { pattern.matches(it) }
            val warnMessage = if (lineNumber != -1) run {
                val lineContent = lines[lineNumber].trimIndent()
                val newContent = lineContent.replace(path.name, "")
                """
                |Deprecated DSL found on ${buildScript.absolutePath}${File.pathSeparator}${lineNumber + 1}:
                |Found: "${lineContent}"
                |Expected: "${newContent}"
                |Please, change the path to avoid this warning.
                |
            """.trimMargin()
            } else
                """
                |Deprecated DSL is used for pod "$name".
                |Please, change its path from ${path.path} to ${path.parentFile.path} 
                |
            """.trimMargin()
            project.logger.warn(warnMessage)
            podSource = path.parentFile
        }
        addToPods(
            project.objects.newInstance(
                CocoapodsDependency::class.java,
                name,
                moduleName
            ).apply {
                this.headers = headers
                this.version = version
                source = podSource?.let { Path(it) }
            }
        )
    }

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    fun pod(name: String, configure: CocoapodsDependency.() -> Unit) {
        // Empty string will lead to an attempt to create two podDownload tasks.
        // One is original podDownload and second is podDownload + pod.name
        require(name.isNotEmpty()) { "Please provide not empty pod name to avoid ambiguity" }
        val dependency = project.objects.newInstance(CocoapodsDependency::class.java, name, name.asModuleName())
        dependency.configure()
        addToPods(dependency)
    }

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    fun pod(name: String, configure: Action<CocoapodsDependency>) = pod(name) {
        configure.execute(this)
    }

    private fun addToPods(dependency: CocoapodsDependency) {
        val name = dependency.name
        check(_pods.findByName(name) == null) { "Project already has a CocoaPods dependency with name $name" }
        _pods.add(dependency)
    }

    abstract class CocoapodsDependency @Inject constructor(
        private val name: String,
        @get:Input var moduleName: String
    ) : Named {

        @get:Optional
        @get:Input
        var headers: String? = null

        @get:Optional
        @get:Input
        var version: String? = null

        @get:Optional
        @get:Nested
        var source: PodLocation? = null

        @get:Internal
        var extraOpts: List<String> = listOf()

        @get:Internal
        var packageName: String = "cocoapods.$moduleName"

        @Input
        override fun getName(): String = name

        /**
         * Path to local pod
         */
        fun path(podspecDirectory: String): PodLocation = Path(File(podspecDirectory))

        /**
         * Path to local pod
         */
        fun path(podspecDirectory: File): PodLocation = Path(podspecDirectory)

        /**
         * Configure pod from git repository. The podspec file is expected to be in the repository root.
         */
        @JvmOverloads
        fun git(url: String, configure: (Git.() -> Unit)? = null): PodLocation {
            val git = Git(URI(url))
            if (configure != null) {
                git.configure()
            }
            return git
        }

        /**
         * Configure pod from git repository. The podspec file is expected to be in the repository root.
         */
        fun git(url: String, configure: Action<Git>) = git(url) {
            configure.execute(this)
        }

        sealed class PodLocation {
            @Internal
            internal abstract fun getPodSourcePath(): String

            data class Path(
                @get:InputDirectory
                @get:IgnoreEmptyDirectories
                val dir: File
            ) : PodLocation() {
                override fun getPodSourcePath() = ":path => '${dir.absolutePath}'"
            }

            data class Git(
                @get:Input val url: URI,
                @get:Input @get:Optional var branch: String? = null,
                @get:Input @get:Optional var tag: String? = null,
                @get:Input @get:Optional var commit: String? = null
            ) : PodLocation() {
                override fun getPodSourcePath() = buildString {
                    append(":git => '$url'")
                    when {
                        branch != null -> append(", :branch => '$branch'")
                        tag != null -> append(", :tag => '$tag'")
                        commit != null -> append(", :commit => '$commit'")
                    }
                }
            }
        }
    }

    data class PodspecPlatformSettings(
        private val name: String,
        @get:Optional @get:Input var deploymentTarget: String? = null
    ) : Named {

        @Input
        override fun getName(): String = name
    }

    companion object {
        private fun String.asModuleName() = this
            .split("/")[0]     // Pick the module name from a subspec name.
            .replace('-', '_') // Support pods with dashes in names (see https://github.com/JetBrains/kotlin-native/issues/2884).
    }
}

// TODO figure out the way for Gradle Kotlin DSL to generate this for us
// Also find a better location for this
fun KotlinNativeXCFrameworkConfig.withPodspec(configure: CocoapodsPodspecExtension.() -> Unit) {
    val extension = findExtension<CocoapodsPodspecExtension>(KotlinCocoapodsPlugin.ARTIFACTS_PODSPEC_EXTENSION_NAME)

    checkNotNull(extension) { "CocoaPods plugin should be applied before using `${KotlinCocoapodsPlugin.ARTIFACTS_PODSPEC_EXTENSION_NAME}` extension" }

    extension.configure()
}