/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.utils

import org.jetbrains.kotlin.analysis.api.fir.symbols.KtFirSymbol
import org.jetbrains.kotlin.analysis.api.fir.types.KtFirType
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.LLFirResolveSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure.LLFirModuleData
import org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure.llFirModuleData
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.fir.dispatchReceiverClassOrNull
import org.jetbrains.kotlin.fir.resolve.toFirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.arrayElementType
import org.jetbrains.kotlin.fir.types.isArrayOrPrimitiveArray

val KtSymbol.firSymbol: FirBasedSymbol<*>
    get() {
        require(this is KtFirSymbol<*>)
        return this.firSymbol
    }

/**
 * Returns whether the given KtType is an array or a primitive array type or not.
 */
fun KtType.isArrayOrPrimitiveArray(): Boolean {
    val firType = this as? KtFirType ?: return false
    return firType.coneType.isArrayOrPrimitiveArray
}

fun KtType.isNestedArray(): Boolean {
    if (!isArrayOrPrimitiveArray()) return false
    val firType = this as? KtFirType ?: return false
    return firType.coneType.arrayElementType()?.isArrayOrPrimitiveArray == true
}

fun FirBasedSymbol<*>.getContainingKtModule(firResolveSession: LLFirResolveSession): KtModule {
    val target = when (this) {
        is FirCallableSymbol -> {
            // callable fake overrides have use-site FirModuleData
            dispatchReceiverClassOrNull()?.toFirRegularClassSymbol(firResolveSession.useSiteFirSession) ?: this
        }
        else -> this
    }
    return target.llFirModuleData.ktModule
}

fun KtSymbol.getContainingKtModule(firResolveSession: LLFirResolveSession): KtModule =
    firSymbol.getContainingKtModule(firResolveSession)