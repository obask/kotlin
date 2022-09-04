/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy.KotlinTargetHierarchy
import org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy.KotlinTargetHierarchyDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail

class KotlinTargetHierarchyTest {
    @Test
    fun `test - hierarchy default - targets from all families`() {
        val project = buildProjectWithMPP {
            kotlin {
                hierarchy.default()
                iosArm32()
                iosArm64()
                iosX64()
                iosSimulatorArm64()

                tvosArm64()
                tvosX64()

                watchosArm32()
                watchosArm64()

                macosX64()
                macosArm64()

                linuxX64()
                linuxArm32Hfp()

                mingwX64()
                mingwX86()

                androidNativeArm32()
                androidNativeArm64()
            }
        }

        val kotlin = project.multiplatformExtension

        assertEquals(
            stringSetOf(
                "androidNativeArm32Main",
                "androidNativeArm64Main",
                "iosArm32Main",
                "iosArm64Main",
                "iosSimulatorArm64Main",
                "iosX64Main",
                "linuxArm32HfpMain",
                "linuxX64Main",
                "macosArm64Main",
                "macosX64Main",
                "mingwX64Main",
                "mingwX86Main",
                "nativeMain",
                "tvosArm64Main",
                "tvosX64Main",
                "watchosArm32Main",
                "watchosArm64Main"
            ),
            kotlin.dependingSourceSetNames("commonMain")
        )

        assertEquals(
            stringSetOf(
                "androidNativeArm32Test",
                "androidNativeArm64Test",
                "iosArm32Test",
                "iosArm64Test",
                "iosSimulatorArm64Test",
                "iosX64Test",
                "linuxArm32HfpTest",
                "linuxX64Test",
                "macosArm64Test",
                "macosX64Test",
                "mingwX64Test",
                "mingwX86Test",
                "nativeTest",
                "tvosArm64Test",
                "tvosX64Test",
                "watchosArm32Test",
                "watchosArm64Test"
            ), kotlin.dependingSourceSetNames("commonTest")
        )

        assertEquals(
            stringSetOf("androidNativeMain", "appleMain", "linuxMain", "windowsMain"),
            kotlin.dependingSourceSetNames("nativeMain")
        )

        assertEquals(
            stringSetOf("androidNativeTest", "appleTest", "linuxTest", "windowsTest"),
            kotlin.dependingSourceSetNames("nativeTest")
        )

        assertEquals(
            stringSetOf("iosMain", "macosMain", "tvosMain", "watchosMain"),
            kotlin.dependingSourceSetNames("appleMain")
        )

        assertEquals(
            stringSetOf("iosTest", "macosTest", "tvosTest", "watchosTest"),
            kotlin.dependingSourceSetNames("appleTest")
        )

        assertEquals(
            stringSetOf("iosArm32Main", "iosArm64Main", "iosSimulatorArm64Main", "iosX64Main"),
            kotlin.dependingSourceSetNames("iosMain")
        )

        assertEquals(
            stringSetOf("iosArm32Test", "iosArm64Test", "iosSimulatorArm64Test", "iosX64Test"),
            kotlin.dependingSourceSetNames("iosTest")
        )

        assertEquals(
            stringSetOf("tvosArm64Main", "tvosX64Main"),
            kotlin.dependingSourceSetNames("tvosMain")
        )

        assertEquals(
            stringSetOf("tvosArm64Test", "tvosX64Test"),
            kotlin.dependingSourceSetNames("tvosTest")
        )

        assertEquals(
            stringSetOf("watchosArm32Main", "watchosArm64Main"),
            kotlin.dependingSourceSetNames("watchosMain")
        )

        assertEquals(
            stringSetOf("watchosArm32Test", "watchosArm64Test"),
            kotlin.dependingSourceSetNames("watchosTest")
        )

        assertEquals(
            stringSetOf("linuxArm32HfpMain", "linuxX64Main"),
            kotlin.dependingSourceSetNames("linuxMain")
        )

        assertEquals(
            stringSetOf("linuxArm32HfpTest", "linuxX64Test"),
            kotlin.dependingSourceSetNames("linuxTest")
        )
    }

    @Test
    fun `test - hierarchy default - only linuxX64`() {
        val project = buildProjectWithMPP {
            kotlin {
                hierarchy.default()
                linuxX64()
            }
        }

        val kotlin = project.multiplatformExtension

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val nativeMain = kotlin.sourceSets.getByName("nativeMain")
        val nativeTest = kotlin.sourceSets.getByName("nativeTest")
        val linuxMain = kotlin.sourceSets.getByName("linuxMain")
        val linuxTest = kotlin.sourceSets.getByName("linuxTest")
        val linuxX64Main = kotlin.sourceSets.getByName("linuxX64Main")
        val linuxX64Test = kotlin.sourceSets.getByName("linuxX64Test")

        assertEquals(
            setOf(commonMain, commonTest, nativeMain, nativeTest, linuxMain, linuxTest, linuxX64Main, linuxX64Test),
            kotlin.sourceSets.toSet()
        )
    }

    @Test
    fun `test - KotlinTargetHierarchyDescriptor - extend`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("base") }
            .extend {
                group("base") {
                    group("extension")
                }
            }

        val project = buildProjectWithMPP()
        val linuxX64 = project.multiplatformExtension.linuxX64()
        val hierarchies = descriptor.hierarchies(linuxX64.compilations.getByName("main"))

        assertEquals(
            setOf(
                KotlinTargetHierarchy(
                    "base", setOf(
                        KotlinTargetHierarchy("extension", emptySet())
                    )
                )
            ),
            hierarchies
        )
    }

    @Test
    fun `test - KotlinTargetHierarchyDescriptor - extend - with new root`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("base") }
            .extend {
                group("newRoot") {
                    group("base") {
                        group("extension")
                    }
                }
            }

        val project = buildProjectWithMPP()
        val linuxX64 = project.multiplatformExtension.linuxX64()
        val hierarchies = descriptor.hierarchies(linuxX64.compilations.getByName("main"))

        val hierarchy = hierarchies.single()

        assertEquals(
            KotlinTargetHierarchy(
                "newRoot", setOf(
                    KotlinTargetHierarchy(
                        "base", setOf(
                            KotlinTargetHierarchy("extension", emptySet())
                        )
                    )
                )
            ),
            hierarchy
        )
    }

    @Test
    fun `test - KotlinTargetHierarchyDescriptor - extend - with new two root and two extensions`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("base") }
            .extend {
                group("newRoot1") {
                    group("base") {
                        group("extension1")
                    }
                }
            }.extend {
                group("newRoot2") {
                    group("base") {
                        group("extension2")
                    }
                }
            }

        val project = buildProjectWithMPP()
        val linuxX64 = project.multiplatformExtension.linuxX64()
        val hierarchies = descriptor.hierarchies(linuxX64.compilations.getByName("main"))

        if (hierarchies.size != 2)
            fail("Expected two hierarchies: Found $hierarchies")

        assertEquals(
            setOf(
                KotlinTargetHierarchy(
                    "newRoot1", setOf(
                        KotlinTargetHierarchy(
                            "base", setOf(
                                KotlinTargetHierarchy("extension1", emptySet()),
                                KotlinTargetHierarchy("extension2", emptySet())
                            )
                        )
                    )
                ),
                KotlinTargetHierarchy(
                    "newRoot2", setOf(
                        KotlinTargetHierarchy(
                            "base", setOf(
                                KotlinTargetHierarchy("extension1", emptySet()),
                                KotlinTargetHierarchy("extension2", emptySet())
                            )
                        )
                    )
                )
            ),
            hierarchies
        )

        fun KotlinTargetHierarchy.collectChildren(): List<KotlinTargetHierarchy> {
            return children.toList() + children.flatMap { it.collectChildren() }
        }

        /* Check that all equal hierarchies are even the same instance */
        val allNodes = hierarchies.flatMap { it.collectChildren() }
        allNodes.forEach { node ->
            val equalNodes = allNodes.filter { otherNode -> otherNode == node }
            equalNodes.forEach { equalNode ->
                assertSame(node, equalNode, "Expected equal nodes to be the same instance")
            }
        }
    }

    @Test
    fun `test - hierarchy set - extend`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("common") { group("base") } }
        val project = buildProjectWithMPP {
            kotlin {
                hierarchy.apply(descriptor) { group("base") { group("extension") } }
                linuxX64()
            }
        }

        val kotlin = project.multiplatformExtension

        assertEquals(
            stringSetOf("baseMain", "linuxX64Main"), kotlin.dependingSourceSetNames("commonMain")
        )

        assertEquals(
            stringSetOf("extensionMain"), kotlin.dependingSourceSetNames("baseMain")
        )

        assertEquals(
            stringSetOf("linuxX64Main"), kotlin.dependingSourceSetNames("extensionMain")
        )

        assertEquals(
            stringSetOf(), kotlin.dependingSourceSetNames("linuxX64Main")
        )
    }


    @Test
    fun `test - hierarchy set - extend - with new root`() {
        val descriptor = KotlinTargetHierarchyDescriptor {
            group("common") {
                group("base")
            }
        }

        val project = buildProjectWithMPP {
            kotlin {
                hierarchy.apply(descriptor) {
                    group("newRoot") {
                        group("base") { group("extension") }
                    }
                }
                linuxX64()
            }
        }

        val kotlin = project.multiplatformExtension

        assertEquals(
            stringSetOf("baseMain"), kotlin.dependingSourceSetNames("newRootMain")
        )

        assertEquals(
            stringSetOf("baseMain", "linuxX64Main"), kotlin.dependingSourceSetNames("commonMain")
        )

        assertEquals(
            stringSetOf("extensionMain"), kotlin.dependingSourceSetNames("baseMain")
        )

        assertEquals(
            stringSetOf("linuxX64Main"), kotlin.dependingSourceSetNames("extensionMain")
        )

        assertEquals(
            stringSetOf(), kotlin.dependingSourceSetNames("linuxX64Main")
        )
    }

}

private fun KotlinMultiplatformExtension.dependingSourceSetNames(sourceSetName: String) =
    dependingSourceSetNames(sourceSets.getByName(sourceSetName))

private fun KotlinMultiplatformExtension.dependingSourceSetNames(sourceSet: KotlinSourceSet) =
    sourceSets.filter { sourceSet in it.dependsOn }.map { it.name }.toStringSet()


/* StringSet: Special Set implementation, which makes it easy to copy and paste after assertions fail */

private fun stringSetOf(vararg values: String) = StringSet(values.toSet())

private fun Iterable<String>.toStringSet() = StringSet(this.toSet())

private data class StringSet(private val set: Set<String>) : Set<String> by set {
    override fun toString(): String {
        return "stringSetOf(" + set.joinToString(", ") { "\"$it\"" } + ")"
    }
}
