/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bitcode

import kotlinBuildProperties
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.ExecClang
import org.jetbrains.kotlin.cpp.*
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.testing.native.GoogleTestExtension
import org.jetbrains.kotlin.utils.Maybe
import org.jetbrains.kotlin.utils.asMaybe
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalStdlibApi::class)
private val String.capitalized: String
    get() = replaceFirstChar { it.uppercase() }

private fun String.snakeCaseToUpperCamelCase() = split('_').joinToString(separator = "") { it.capitalized }

private fun fullTaskName(name: String, targetName: String, sanitizer: SanitizerKind?) = "${targetName}${name.snakeCaseToUpperCamelCase()}${sanitizer.taskSuffix}"

private val SanitizerKind?.taskSuffix
    get() = when (this) {
        null -> ""
        SanitizerKind.ADDRESS -> "_ASAN"
        SanitizerKind.THREAD -> "_TSAN"
    }

private val SanitizerKind?.dirSuffix
    get() = when (this) {
        null -> ""
        SanitizerKind.ADDRESS -> "-asan"
        SanitizerKind.THREAD -> "-tsan"
    }

private val SanitizerKind?.description
    get() = when (this) {
        null -> ""
        SanitizerKind.ADDRESS -> " with ASAN"
        SanitizerKind.THREAD -> " with TSAN"
    }

private val KonanTarget.executableExtension
    get() = when (this) {
        is KonanTarget.MINGW_X64 -> ".exe"
        is KonanTarget.MINGW_X86 -> ".exe"
        else -> ""
    }

private abstract class RunGTestSemaphore : BuildService<BuildServiceParameters.None>
private abstract class CompileTestsSemaphore : BuildService<BuildServiceParameters.None>

/**
 * Building and testing C/C++ modules.
 *
 * @see CompileToBitcodePlugin gradle plugin that creates this extension
 */
abstract class CompileToBitcodeExtension @Inject constructor(val project: Project) {
    // TODO: These should be set by the plugin users.
    private val DEFAULT_CPP_FLAGS = listOfNotNull(
            "-gdwarf-2".takeIf { project.kotlinBuildProperties.getBoolean("kotlin.native.isNativeRuntimeDebugInfoEnabled", false) },
            "-std=c++17",
            "-Werror",
            "-O2",
            "-fno-aligned-allocation", // TODO: Remove when all targets support aligned allocation in C++ runtime.
            "-Wall",
            "-Wextra",
            "-Wno-unused-parameter",  // False positives with polymorphic functions.
    )

    private val compilationDatabase = project.extensions.getByType<CompilationDatabaseExtension>()
    private val execClang = project.extensions.getByType<ExecClang>()
    private val platformManager = project.extensions.getByType<PlatformManager>()

    // googleTestExtension is only used if testsGroup is used.
    private val googleTestExtension by lazy { project.extensions.getByType<GoogleTestExtension>() }

    // A shared service used to limit parallel execution of test binaries.
    private val runGTestSemaphore = project.gradle.sharedServices.registerIfAbsent("runGTestSemaphore", RunGTestSemaphore::class.java) {
        // Probably can be made configurable if test reporting moves away from simple gtest stdout dumping.
        maxParallelUsages.set(1)
    }

    // TODO: remove when tests compilation does not consume so much memory.
    private val compileTestsSemaphore = project.gradle.sharedServices.registerIfAbsent("compileTestsSemaphore", CompileTestsSemaphore::class.java) {
        maxParallelUsages.set(5)
    }

    /**
     * Base class for [Module] and [CustomModule].
     *
     * @property name name of the module.
     * @property owner [Target] for which this module is created.
     * @property target target for which module is configured.
     * @property sanitizer optional sanitizer for the [target].
     */
    abstract class AbstractModule(
            val owner: Target,
            val name: String,
    ) {
        val target by owner::target
        val sanitizer by owner::sanitizer
        protected val project by owner.owner::project
        protected val objects by project::objects

        // TODO: Should this go away?
        val outputGroup = objects.property<String>().apply {
            convention("main")
        }

        /**
         * Gradle task that produces linked module [name] for [target] with optional [sanitizer].
         */
        val task: TaskProvider<CompileToBitcode> = project.tasks.register<CompileToBitcode>(fullTaskName(name, target.name, sanitizer), target, sanitizer.asMaybe).apply {
            configure {
                when (outputGroup.get()) {
                    "test" -> this.group = VERIFICATION_BUILD_TASK_GROUP
                    "main" -> this.group = BUILD_TASK_GROUP
                }
                description = "Compiles '$name' to bitcode for $target${sanitizer.description}"
                dependsOn(":kotlin-native:dependencies:update") // TODO: really needs only the current target
            }
        }

        override fun equals(other: Any?): Boolean {
            val rhs = other as? AbstractModule ?: return false
            return name == rhs.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    /**
     * Configure a module for [target] with optional [sanitizer].
     *
     * @param owner owner of this module
     * @param name name of this module
     */
    open class Module @Inject constructor(
        owner: Target,
        name: String,
    ) : AbstractModule(owner, name) {
        // TODO: Should this go away?
        val srcRoot = objects.directoryProperty().apply {
            convention(project.layout.projectDirectory.dir("src/$name"))
        }

        init {
            task.configure {
                this.moduleName.set(name)
                this.outputFile.convention(moduleName.flatMap { project.layout.buildDirectory.file("bitcode/${outputGroup.get()}/$target${sanitizer.dirSuffix}/$it.bc") })
                this.outputDirectory.convention(moduleName.flatMap { project.layout.buildDirectory.dir("bitcode/${outputGroup.get()}/$target${sanitizer.dirSuffix}/$it") })
                this.compiler.convention("clang++")
                this.inputFiles.from(srcRoot.asFile.get().resolve("cpp"))
                this.inputFiles.include("**/*.cpp", "**/*.mm")
                this.inputFiles.exclude("**/*Test.cpp", "**/*TestSupport.cpp", "**/*Test.mm", "**/*TestSupport.mm")
                this.headersDirs.from(this.inputFiles.dir)
                this.compilerWorkingDirectory.set(project.layout.projectDirectory.dir("src"))
            }
        }
    }

    /**
     * Configure a group of tests for [target] with optional [sanitizer].
     *
     * @property owner [Target] for which this [TestsGroup] is created.
     * @property testTaskName name of this [TestsGroup].
     * @property target target for which the test group is configured.
     * @property sanitizer optional sanitizer for the [target].
     */
    abstract class TestsGroup @Inject constructor(
            val owner: Target,
            val testTaskName: String,
    ) {
        val target by owner::target
        val sanitizer by owner::sanitizer
        private val project by owner.owner::project

        /**
         * A list of modules from which to extract tests.
         *
         * All of these modules are linked together.
         */
        abstract val testedModules: ListProperty<String>

        /**
         * A list of supporting modules (e.g. gtest).
         *
         * All of these modules are linked together, but no tests are extracted from them.
         */
        abstract val testSupportModules: ListProperty<String>

        /**
         * A module that contains main().
         *
         * Links into final executable.
         */
        abstract val testLauncherModule: Property<String>

        /**
         * Gradle task that runs [testTaskName] tests group for [target] with optional [sanitizer].
         */
        val task = project.tasks.register<RunGTest>(fullTaskName(testTaskName, target.name, sanitizer)) {
            description = "Runs tests group '$testTaskName' for $target${sanitizer.description}"
            group = VERIFICATION_TASK_GROUP
        }
    }

    /**
     * Configure modules for [target] with optional [sanitizer].
     *
     * @property owner [CompileToBitcodeExtension] for which this [Target] was created.
     * @property target target for which modules are configured.
     * @property sanitizer optional sanitizer for the [target].
     */
    open class Target @Inject constructor(
            val owner: CompileToBitcodeExtension,
            val target: KonanTarget,
            private val _sanitizer: Maybe<SanitizerKind>,
    ) {
        val sanitizer by _sanitizer::orNull
        private val project by owner::project
        private val objects by project::objects

        private val modules = objects.namedDomainObjectSet<AbstractModule>(AbstractModule::class)

        private fun addToCompdb(compileTask: CompileToBitcode) {
            // No need to generate compdb entry for sanitizers.
            if (sanitizer != null) {
                return
            }
            owner.compilationDatabase.target(target) {
                entry {
                    val args = listOf(owner.execClang.resolveExecutable(compileTask.compiler.get())) + compileTask.compilerFlags.get() + owner.execClang.clangArgsForCppRuntime(target.name)
                    directory.set(compileTask.compilerWorkingDirectory)
                    files.setFrom(compileTask.inputFiles)
                    arguments.set(args)
                    // Only the location of output file matters, compdb does not depend on the compilation result.
                    output.set(compileTask.outputFile.locationOnly.map { it.asFile.absolutePath })
                }
            }
        }

        /**
         * Configure module named [name] for [target] with optional [sanitizer].
         *
         * @param name module name; must be unique
         * @param action action to apply to the module
         *
         * @throws IllegalArgumentException if a module named [name] already exists.
         */
        fun module(name: String, action: Action<in Module>): Module {
            val module = project.objects.newInstance<Module>(this, name).apply {
                task.configure {
                    compilerArgs.set(owner.owner.DEFAULT_CPP_FLAGS)
                }
                action.execute(this)
                // TODO: Get rid of get()
                addToCompdb(task.get())
            }
            val added = modules.add(module)
            require(added) {
                "Module named $name already exists"
            }
            return module
        }

        /**
         * Get module named [name] for [target] with optional [sanitizer].
         *
         * @param name module name
         *
         * @throws UnknownDomainObjectException if module named [name] is not found.
         */
        fun module(name: String): Provider<Module> = modules.withType<Module>().named(name)

        /**
         * Configure module for [target] with optional [sanitizer].
         */
        fun module(name: String, srcRoot: File = project.file("src/$name"), outputGroup: String = "main", configurationBlock: CompileToBitcode.() -> Unit = {}) {
            module(name, action = {
                this.outputGroup.set(outputGroup)
                this.srcRoot.set(srcRoot)
                this.task.configure {
                    configurationBlock()
                }
            })
        }

        fun testsGroup(
                testTaskName: String,
                action: Action<in TestsGroup>,
        ) {
            val testsGroup = project.objects.newInstance<TestsGroup>(this, testTaskName).apply {
                testSupportModules.convention(listOf("googletest", "googlemock"))
                testLauncherModule.convention("test_support")
                action.execute(this)
            }
            val target = this.target
            val sanitizer = this.sanitizer
            val testName = fullTaskName(testTaskName, target.name, sanitizer)
            val testedTasks = testsGroup.testedModules.get().map {
                val name = fullTaskName(it, target.name, sanitizer)
                project.tasks.getByName(name) as CompileToBitcode
            }
            val compileToBitcodeTasks = testedTasks.mapNotNull {
                val name = "${it.name}TestBitcode"
                val task = project.tasks.findByName(name) as? CompileToBitcode
                        ?: project.tasks.create(name, CompileToBitcode::class.java, it.target, it.sanitizer.asMaybe).apply {
                            this.moduleName.set(it.moduleName)
                            this.outputFile.convention(moduleName.flatMap { project.layout.buildDirectory.file("bitcode/test/$target${sanitizer.dirSuffix}/${it}Tests.bc") })
                            this.outputDirectory.convention(moduleName.flatMap { project.layout.buildDirectory.dir("bitcode/test/$target${sanitizer.dirSuffix}/${it}Tests") })
                            this.compiler.convention("clang++")
                            this.compilerArgs.set(it.compilerArgs)
                            this.inputFiles.from(it.inputFiles.dir)
                            this.inputFiles.include("**/*Test.cpp", "**/*TestSupport.cpp", "**/*Test.mm", "**/*TestSupport.mm")
                            this.headersDirs.setFrom(it.headersDirs)
                            this.headersDirs.from(owner.googleTestExtension.headersDirs)
                            this.compilerWorkingDirectory.set(it.compilerWorkingDirectory)
                            this.group = VERIFICATION_BUILD_TASK_GROUP
                            this.description = "Compiles '${it.name}' tests to bitcode for $target${sanitizer.description}"

                            dependsOn(":kotlin-native:dependencies:update")
                            dependsOn("downloadGoogleTest")

                            addToCompdb(this)
                        }
                if (task.inputFiles.count() == 0) null
                else task
            }
            val testFrameworkTasks = testsGroup.testSupportModules.get().map {
                val name = fullTaskName(it, target.name, sanitizer)
                project.tasks.getByName(name) as CompileToBitcode
            }

            val testSupportTask = testsGroup.testLauncherModule.get().let {
                val name = fullTaskName(it, target.name, sanitizer)
                project.tasks.getByName(name) as CompileToBitcode
            }

            val compileTask = project.tasks.register<CompileToExecutable>("${testName}Compile") {
                description = "Compile tests group '$testTaskName' for $target${sanitizer.description}"
                group = VERIFICATION_BUILD_TASK_GROUP
                this.target.set(target)
                this.sanitizer.set(sanitizer)
                this.outputFile.set(project.layout.buildDirectory.file("bin/test/${target}/$testName${target.executableExtension}"))
                this.llvmLinkFirstStageOutputFile.set(project.layout.buildDirectory.file("bitcode/test/$target/$testName-firstStage.bc"))
                this.llvmLinkOutputFile.set(project.layout.buildDirectory.file("bitcode/test/$target/$testName.bc"))
                this.compilerOutputFile.set(project.layout.buildDirectory.file("obj/$target/$testName.o"))
                this.mimallocEnabled.set(testsGroup.testedModules.get().any { it.contains("mimalloc") })
                this.mainFile.set(testSupportTask.outputFile)
                val tasksToLink = (compileToBitcodeTasks + testedTasks + testFrameworkTasks)
                this.inputFiles.setFrom(tasksToLink.map { it.outputFile })

                usesService(owner.compileTestsSemaphore)
            }

            testsGroup.task.configure {
                this.testName.set(testName)
                executable.set(compileTask.flatMap { it.outputFile })
                dependsOn(compileTask)
                reportFileUnprocessed.set(project.layout.buildDirectory.file("testReports/$testName/report.xml"))
                reportFile.set(project.layout.buildDirectory.file("testReports/$testName/report-with-prefixes.xml"))
                filter.set(project.findProperty("gtest_filter") as? String)
                tsanSuppressionsFile.set(project.layout.projectDirectory.file("tsan_suppressions.txt"))

                usesService(owner.runGTestSemaphore)
            }

            allTestsTask.configure {
                dependsOn(testsGroup.task)
            }
        }

        /**
         * Gradle task that builds all main modules for [target] with optional [sanitizer].
         */
        val allMainModulesTask = project.tasks.register("${target}${project.name.capitalized}${sanitizer.taskSuffix}") {
            description = "Build all main modules of ${project.name.capitalized} for $target${sanitizer.description}"
            group = BUILD_TASK_GROUP
            dependsOn(modules.matching {
                it.outputGroup.get() == "main"
            }.map {
                it.task
            })
        }

        /**
         * Gradle task that tests all modules for [target] with optional [sanitizer].
         */
        val allTestsTask = project.tasks.register("${target}${project.name.capitalized}Tests${sanitizer.taskSuffix}") {
            description = "Runs all tests of ${project.name.capitalized} for $target${sanitizer.description}"
            group = VERIFICATION_TASK_GROUP
        }
    }

    protected abstract val targets: MapProperty<Pair<KonanTarget, Maybe<SanitizerKind>>, Target>

    private fun targetGetOrPut(target: KonanTarget, sanitizer: SanitizerKind?) = targets.getting(target to sanitizer.asMaybe).orNull
            ?: project.objects.newInstance<Target>(this, target, sanitizer.asMaybe).apply {
                targets.put(target to sanitizer.asMaybe, this)
            }

    /**
     * Get [modules configuration][Target] for [target] with optional [sanitizer] and apply [action] to it.
     *
     * @param target target to configure.
     * @param sanitizer optional sanitizer for [target].
     * @param action action to apply to configuration.
     */
    fun target(target: KonanTarget, sanitizer: SanitizerKind?, action: Action<in Target>) = targetGetOrPut(target, sanitizer).apply {
        action.execute(this)
    }

    /**
     * Get [modules configuration][Target] for [target] with optional [sanitizer].
     *
     * @param target target to configure.
     * @param sanitizer optional sanitizer for [target].
     */
    fun target(target: KonanTarget, sanitizer: SanitizerKind?) = this.target(target, sanitizer) {}

    /**
     * Get [modules configurations][Target] for all known targets and sanitizers and apply [action] to each.
     *
     * @param action action to apply to configurations.
     */
    fun allTargets(action: Action<in Target>) = platformManager.enabled.map { target ->
        val sanitizers: List<SanitizerKind?> = target.supportedSanitizers() + listOf(null)
        sanitizers.forEach { sanitizer ->
            this.target(target, sanitizer, action)
        }
    }

    /**
     * Get [modules configurations][Target] for all known targets and sanitizers.
     */
    val allTargets
        get() = allTargets {}

    /**
     * Get [modules configuration][Target] for [host target][HostManager.host] and apply [action] to it.
     *
     * @param action action to apply to configuration.
     */
    fun hostTarget(action: Action<in Target>) = target(HostManager.host, null, action)

    /**
     * Get [compilation database configuration][Target] for [host target][HostManager.host].
     */
    val hostTarget
        get() = hostTarget {}

    companion object {
        const val BUILD_TASK_GROUP = LifecycleBasePlugin.BUILD_GROUP
        const val VERIFICATION_TASK_GROUP = LifecycleBasePlugin.VERIFICATION_GROUP
        const val VERIFICATION_BUILD_TASK_GROUP = "verification build"
    }
}

/**
 * Building and testing C/C++ modules.
 *
 * Creates [CompileToBitcodeExtension] extension named `bitcode`.
 * Also applies [CompilationDatabasePlugin].
 *
 * @see CompileToBitcodeExtension extension that this plugin creates.
 */
open class CompileToBitcodePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply<LifecycleBasePlugin>()
        target.apply<CompilationDatabasePlugin>()
        target.extensions.create<CompileToBitcodeExtension>("bitcode", target)
    }
}