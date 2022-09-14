/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.internal

import org.gradle.api.provider.Provider

internal class ConfigurationTimePropertiesAccessorG71 : ConfigurationTimePropertiesAccessor {
    internal class ConfigurationTimePropertiesAccessorVariantFactoryG71 :
        ConfigurationTimePropertiesAccessor.ConfigurationTimePropertiesAccessorVariantFactory {
        override fun getInstance(): ConfigurationTimePropertiesAccessor = ConfigurationTimePropertiesAccessorG71()
    }

    override fun Provider<String>.usedAtConfigurationTime(): Provider<String> = forUseAtConfigurationTime()
}