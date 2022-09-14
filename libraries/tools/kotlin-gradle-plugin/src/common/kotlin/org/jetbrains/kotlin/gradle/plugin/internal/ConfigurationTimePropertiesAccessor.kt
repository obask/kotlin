/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.internal

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.VariantImplementationFactories
import org.jetbrains.kotlin.gradle.plugin.variantImplementationFactory

internal interface ConfigurationTimePropertiesAccessor {
    fun Provider<String>.usedAtConfigurationTime(): Provider<String>

    interface ConfigurationTimePropertiesAccessorVariantFactory : VariantImplementationFactories.VariantImplementationFactory {
        fun getInstance(): ConfigurationTimePropertiesAccessor
    }
}

internal class DefaultConfigurationTimePropertiesAccessorVariantFactory :
    ConfigurationTimePropertiesAccessor.ConfigurationTimePropertiesAccessorVariantFactory {
    override fun getInstance(): ConfigurationTimePropertiesAccessor = DefaultConfigurationTimePropertiesAccessor()
}

internal class DefaultConfigurationTimePropertiesAccessor : ConfigurationTimePropertiesAccessor {
    override fun Provider<String>.usedAtConfigurationTime(): Provider<String> = this
}

internal val Gradle.configurationTimePropertiesAccessor
    get() = gradle
        .variantImplementationFactory<ConfigurationTimePropertiesAccessor.ConfigurationTimePropertiesAccessorVariantFactory>()
        .getInstance()