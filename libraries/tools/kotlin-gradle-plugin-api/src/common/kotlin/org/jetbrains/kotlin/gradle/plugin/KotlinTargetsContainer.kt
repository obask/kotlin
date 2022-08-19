/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer

interface KotlinTargetsContainer {
    val targets: NamedDomainObjectCollection<KotlinTarget>
}

interface KotlinTargetsContainerWithPresets : KotlinTargetsContainer {
    val presets: NamedDomainObjectCollection<KotlinTargetPreset<*>>

    fun <T: KotlinTarget> configureOrCreate(
        targetName: String,
        targetPreset: KotlinTargetPreset<T>,
        configure: T.() -> Unit
    ): T {
        val existingTarget = targets.findByName(targetName)
        when {
            existingTarget?.isProducedFromPreset(targetPreset) ?: false -> {
                @Suppress("UNCHECKED_CAST")
                configure(existingTarget as T)
                return existingTarget
            }

            existingTarget == null -> {
                val newTarget = targetPreset.createTarget(targetName)
                targets.add(newTarget)
                configure(newTarget)
                return newTarget
            }

            else -> {
                throw InvalidUserCodeException(
                    "The target '$targetName' already exists, but it was not created with the '${targetPreset.name}' preset. " +
                            "To configure it, access it by name in `kotlin.targets`" +
                            (" or use the preset function '${existingTarget.preset?.name}'."
                                .takeIf { existingTarget.preset != null } ?: ".")
                )
            }
        }
    }
}

interface KotlinSourceSetContainer {
    val sourceSets: NamedDomainObjectContainer<KotlinSourceSet>
}

internal fun KotlinTarget.isProducedFromPreset(kotlinTargetPreset: KotlinTargetPreset<*>): Boolean =
    preset == kotlinTargetPreset
