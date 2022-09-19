/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.commonizer.*
import org.jetbrains.kotlin.compilerRunner.GradleCliCommonizer
import org.jetbrains.kotlin.compilerRunner.KotlinNativeCommonizerToolRunner
import org.jetbrains.kotlin.compilerRunner.KotlinToolRunner
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.sources.withDependsOnClosure
import org.jetbrains.kotlin.gradle.targets.native.internal.CInteropCommonizerTask.CInteropGist
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.utils.chainedFinalizeValueOnRead
import org.jetbrains.kotlin.gradle.utils.listProperty
import org.jetbrains.kotlin.gradle.utils.property
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import javax.inject.Inject

@CacheableTask
internal open class CInteropCommonizerTask
@Inject constructor(
    private val objectFactory: ObjectFactory,
    private val execOperations: ExecOperations,
    private val projectLayout: ProjectLayout
) : AbstractCInteropCommonizerTask() {

    internal class CInteropGist(
        @get:Input val identifier: CInteropIdentifier,
        @get:Input val konanTarget: KonanTarget,
        sourceSets: Provider<Set<KotlinSourceSet>>,

        @get:Classpath
        val libraryFile: Provider<File>,

        @get:IgnoreEmptyDirectories
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val dependencies: FileCollection
    ) {
        @Suppress("unused") // Used for UP-TO-DATE check
        @get:Input
        val allSourceSetNames: Provider<List<String>> = sourceSets
            .map { it.withDependsOnClosure.map(Any::toString) }

        /** Autogenerated with IDEA */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CInteropGist

            if (identifier != other.identifier) return false
            if (konanTarget != other.konanTarget) return false
            if (libraryFile != other.libraryFile) return false
            if (dependencies != other.dependencies) return false
            if (allSourceSetNames != other.allSourceSetNames) return false

            return true
        }

        /** Autogenerated with IDEA */
        override fun hashCode(): Int {
            var result = identifier.hashCode()
            result = 31 * result + konanTarget.hashCode()
            result = 31 * result + libraryFile.hashCode()
            result = 31 * result + dependencies.hashCode()
            result = 31 * result + allSourceSetNames.hashCode()
            return result
        }

    }

    override val outputDirectory: File get() = projectLayout.buildDirectory.get().asFile.resolve("classes/kotlin/commonizer")

    @get:Internal
    internal val kotlinPluginVersion: Property<String> = objectFactory
        .property<String>()
        .chainedFinalizeValueOnRead()

    @get:Classpath
    internal val commonizerClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:Input
    internal val customJvmArgs: ListProperty<String> = objectFactory
        .listProperty<String>()
        .chainedFinalizeValueOnRead()

    private val runnerSettings: Provider<KotlinNativeCommonizerToolRunner.Settings> = kotlinPluginVersion
        .zip(customJvmArgs) { pluginVersion, customJvmArgs ->
            KotlinNativeCommonizerToolRunner.Settings(
                pluginVersion,
                commonizerClasspath.files,
                customJvmArgs
            )
        }

    private val konanHome = project.file(project.konanHome)
    private val commonizerLogLevel = project.commonizerLogLevel
    private val additionalCommonizerSettings = project.additionalCommonizerSettings

    /**
     * For Gradle Configuration Cache support the Group-to-Dependencies relation should be pre-cached.
     * It is used during execution phase.
     */
    private val nativeDistributionDependenciesMap: Map<CInteropCommonizerGroup, Set<CommonizerDependency>> by lazy {
        getAllInteropsGroups().associateWith { group ->
            (group.targets + group.targets.allLeaves()).flatMapTo(mutableSetOf()) { target ->
                project.getNativeDistributionDependencies(target).map { dependency -> TargetedCommonizerDependency(target, dependency) }
            }
        }
    }

    private val externalDependenciesMap: Map<CInteropCommonizerGroup, Set<CommonizerDependency>> by lazy {
        val multiplatformExtension = project.multiplatformExtensionOrNull ?: return@lazy emptyMap()
        val sourceSets = multiplatformExtension.sourceSets.groupBy { sourceSet ->
            project.getCommonizerTarget(sourceSet)
        }
        getAllInteropsGroups().associateWith { group ->
            (group.targets + group.targets.allLeaves()).flatMapTo(mutableSetOf()) { target ->
                val files = when (target) {
                    is LeafCommonizerTarget -> cinterops
                        .filter { cinterop -> cinterop.identifier in group.interops && cinterop.konanTarget == target.konanTarget }
                        .flatMap { cinterop -> cinterop.dependencies.files }

                    is SharedCommonizerTarget -> sourceSets[target].orEmpty()
                        .filterIsInstance<DefaultKotlinSourceSet>()
                        .flatMap { sourceSet -> project.createCInteropMetadataDependencyClasspath(sourceSet).files }
                }

                files.filter { file -> (file.extension == "klib" || file.isDirectory) && file.exists() }
                    .map { file -> TargetedCommonizerDependency(target, file) }
            }
        }
    }

    @get:Nested
    internal var cinterops = setOf<CInteropGist>()
        private set

    @get:OutputDirectories
    val allOutputDirectories: Set<File>
        get() = getAllInteropsGroups().map { outputDirectory(it) }.toSet()

    @Suppress("unused") // Used for UP-TO-DATE check
    @get:Classpath
    val commonizedNativeDistributionDependencies: Set<File>
        get() = getAllInteropsGroups().flatMap { group -> group.targets }
            .flatMap { target -> project.getNativeDistributionDependencies(target) }
            .toSet()

    fun from(vararg tasks: CInteropProcess) = from(
        tasks.toList()
            .onEach { task -> this.dependsOn(task) }
            .map { task -> task.toGist() }
    )

    internal fun from(vararg cinterop: CInteropGist) {
        from(cinterop.toList())
    }

    internal fun from(cinterops: List<CInteropGist>) {
        this.cinterops += cinterops
    }

    @TaskAction
    protected fun commonizeCInteropLibraries() {
        getAllInteropsGroups().forEach(::commonize)
    }

    private fun commonize(group: CInteropCommonizerGroup) {
        val cinteropsForTarget = cinterops.filter { cinterop -> cinterop.identifier in group.interops }
        outputDirectory(group).deleteRecursively()
        if (cinteropsForTarget.isEmpty()) return

        val commonizerRunner = KotlinNativeCommonizerToolRunner(
            context = KotlinToolRunner.GradleExecutionContext.fromTaskContext(objectFactory, execOperations, logger),
            settings = runnerSettings.get()
        )

        val externalDependencies = externalDependenciesMap[group].orEmpty()
        val nativeDistributionDependencies = getNativeDistributionDependencies(group)
        GradleCliCommonizer(commonizerRunner).commonizeLibraries(
            konanHome = konanHome,
            outputTargets = group.targets,
            inputLibraries = cinteropsForTarget.map { it.libraryFile.get() }.filter { it.exists() }.toSet(),
            dependencyLibraries = externalDependencies + nativeDistributionDependencies,
            outputDirectory = outputDirectory(group),
            logLevel = commonizerLogLevel,
            additionalSettings = additionalCommonizerSettings,
        )
    }

    private fun getNativeDistributionDependencies(group: CInteropCommonizerGroup): Set<CommonizerDependency> {
        val dependencies = nativeDistributionDependenciesMap[group]
        requireNotNull(dependencies) { "Unexpected $group" }

        return dependencies
    }

    @Nested
    internal fun getAllInteropsGroups(): Set<CInteropCommonizerGroup> {
        val dependents = allDependents
        val allScopeSets = dependents.map { it.scopes }.toSet()
        val rootScopeSets = allScopeSets.filter { scopeSet ->
            allScopeSets.none { otherScopeSet -> otherScopeSet != scopeSet && otherScopeSet.containsAll(scopeSet) }
        }

        return rootScopeSets.map { scopeSet ->
            val dependentsForScopes = dependents.filter { dependent ->
                scopeSet.containsAll(dependent.scopes)
            }

            CInteropCommonizerGroup(
                targets = dependentsForScopes.map { it.target }.toSet(),
                interops = dependentsForScopes.flatMap { it.interops }.toSet()
            )
        }.toSet()
    }

    override fun findInteropsGroup(dependent: CInteropCommonizerDependent): CInteropCommonizerGroup? {
        val suitableGroups = getAllInteropsGroups().filter { group ->
            group.interops.containsAll(dependent.interops) && group.targets.contains(dependent.target)
        }

        assert(suitableGroups.size <= 1) {
            "CInteropCommonizerTask: Unnecessary work detected: More than one suitable group found for cinterop dependent."
        }

        return suitableGroups.firstOrNull()
    }

    private val allDependents: Set<CInteropCommonizerDependent> by lazy {
        val multiplatformExtension = project.multiplatformExtensionOrNull ?: return@lazy emptySet()

        val fromSharedNativeCompilations = multiplatformExtension
            .targets.flatMap { target -> target.compilations }
            .filterIsInstance<KotlinSharedNativeCompilation>()
            .mapNotNull { compilation -> CInteropCommonizerDependent.from(compilation) }
            .toSet()

        val fromSourceSets = multiplatformExtension.sourceSets
            .mapNotNull { sourceSet -> CInteropCommonizerDependent.from(project, sourceSet) }
            .toSet()

        val fromSourceSetsAssociateCompilations = multiplatformExtension.sourceSets
            .mapNotNull { sourceSet -> CInteropCommonizerDependent.fromAssociateCompilations(project, sourceSet) }
            .toSet()

        return@lazy (fromSharedNativeCompilations + fromSourceSets + fromSourceSetsAssociateCompilations)
    }
}

private fun CInteropProcess.toGist(): CInteropGist {
    return CInteropGist(
        identifier = settings.identifier,
        konanTarget = konanTarget,
        // FIXME support cinterop with PM20
        sourceSets = project.provider { (settings.compilation as? KotlinCompilation<*>)?.kotlinSourceSets },
        libraryFile = outputFileProvider,
        dependencies = libraries
    )
}
