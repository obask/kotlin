/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irComposite
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.utils.collectNamesForLambda
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.backend.js.utils.prependStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Wraps returnable blocks with returns to composite and replaces returns with assignment to temporary variable + `return Unit`,
 * also, it changes type of returnable block to Unit.
 *
 * ```
 * returnable_block {
 *   ...
 *   return@returnable_block e
 *   ...
 * }: T
 * ```
 *
 * is transformed into
 *
 * ```
 * composite {
 *   val result
 *   returnable_block {
 *     ...
 *     result = e
 *     return@returnable_block Unit
 *     ...
 *   }: Unit
 *   result
 * }: T
 * ```
 */
class JsReturnableBlockLowering(val context: JsIrBackendContext) : BodyLoweringPass {
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        container.transform(JsReturnableBlockTransformer(context, container), null)
    }
}

private class JsReturnableBlockTransformer(val context: JsIrBackendContext, val container: IrDeclaration) :
    IrElementTransformerVoidWithContext()
{
    private var variablesForReturnTargets = mutableMapOf<IrReturnTargetSymbol, IrVariable>()

    /**
     * For each returnable block wrapped in an immediately invoked function expression there is an entry in this map.
     * Returnable blocks generated from [kotlin.internal.InlineOnly]-annotated functions are not wrapped.
     */
    private var wrappedInIIFE = mutableMapOf<IrReturnableBlockSymbol, IrSimpleFunctionSymbol>()

    private val returnTargetSymbolStack = mutableListOf<IrReturnTargetSymbol>()

    private val returnableBlocksWithNonLocalReturns = mutableSetOf<IrReturnableBlockSymbol>()

    private val returnableBlocksWithCapturedThis = mutableMapOf<IrReturnableBlockSymbol, IrValueSymbol>()

    private fun IrReturnTarget.hasNonLocalReturns() = returnableBlocksWithNonLocalReturns.contains(this.symbol)

    override fun visitBlock(expression: IrBlock): IrExpression {
        if (expression !is IrReturnableBlock) return super.visitBlock(expression)

        val maybeWrappedInIIFE = transformChildrenAndWrapInIifeIfNeeded(expression)

        val variable = variablesForReturnTargets.remove(expression.symbol) ?: return maybeWrappedInIIFE

        expression.type = context.irBuiltIns.unitType
        return context.createIrBuilder(expression.symbol).irComposite(expression, expression.origin, variable.type) {
            +variable
            +maybeWrappedInIIFE
            +irGet(variable)
        }
    }

    private fun IrReturnTargetSymbol.markAsCapturingThisIfOtherCapturesThis(other: IrReturnableBlockSymbol) =
        returnableBlocksWithCapturedThis.remove(other)?.also { thisSymbol ->
            if (this is IrReturnableBlockSymbol)
                returnableBlocksWithCapturedThis[this] = thisSymbol
        }

    private fun transformChildrenAndWrapInIifeIfNeeded(expression: IrReturnableBlock): IrExpression {
        val closestReturnTargetSymbol = returnTargetSymbolStack.peek()

        returnTargetSymbolStack.push(expression.symbol)

        // If this feature is enabled, we wrap returnable blocks in immediately invoked function expressions (IIFE) so that
        // when debugging the generated code, the Step Over debugger action would skip the inlined function instead of jumping to its body.
        if (!wrapInIIFE) {
            expression.transformChildrenVoid()
            returnTargetSymbolStack.pop()
            closestReturnTargetSymbol?.markAsCapturingThisIfOtherCapturesThis(expression.symbol)
            return expression
        }

        val inlineFunction = expression.inlineFunctionSymbol!!.owner

        val function = context.irFactory.buildFun {
            returnType = context.dynamicType
            visibility = DescriptorVisibilities.LOCAL
            name = Name.identifier(
                buildString {
                    collectNamesForLambda(inlineFunction)
                }
            )
        }.apply {
            origin = JsLoweredDeclarationOrigin.INLINE_FUNCTION_IIFE
            parent = currentDeclarationParent ?: container as? IrDeclarationParent ?: container.parent
            body = context.irFactory.createBlockBody(expression.startOffset, expression.endOffset, expression.statements)
            body!!.patchDeclarationParents(this)
        }

        wrappedInIIFE[expression.symbol] = function.symbol

        val function0Class = context.ir.symbols.functionN(0)
        val functionType = IrSimpleTypeImpl(function0Class, false, arguments = listOf(context.dynamicType), annotations = emptyList())

        var functionExpression: IrExpression = JsIrBuilder.buildFunctionExpression(functionType, function)

        functionExpression.transformChildrenVoid()
        returnTargetSymbolStack.pop()

        // If the returnable block contained `this`, don't forget to add `.bind(this)` to the function expression.
        closestReturnTargetSymbol?.markAsCapturingThisIfOtherCapturesThis(expression.symbol)?.let { thisSymbol ->
            functionExpression = JsIrBuilder.buildCall(
                target = context.intrinsics.jsBind,
                type = functionType
            ).apply {
                putValueArgument(0, IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisSymbol))
                putValueArgument(1, functionExpression)
            }
        }

        val iife = JsIrBuilder.buildCall(function0Class.owner.invokeFun!!.symbol).apply {
            dispatchReceiver = functionExpression
        }

        if (!expression.hasNonLocalReturns()) return iife

        // If this block has non-local returns that cross the boundary of this block, and the block is wrapped in an IIFE,
        // we need to generate an early return from the IIFE to propagate the non-local return up the call stack.
        //
        // Basically, this code:
        //     inline fun foo(f: () -> Unit) = f()
        //
        //     fun box(): String {
        //         foo {
        //             return "foo" // <-- non-local return from box()
        //         }
        //        return "bar"
        //     }
        //
        // is translated to this:
        //     fun box(): String {
        //         val retVal: String
        //         if ((fun foo() {
        //             if ((fun <anonymous>() {
        //                 retVal = "foo"
        //                 return true // <-- returning true indicates a non-local return
        //             }).invoke())
        //                 return true // <-- return is propagated
        //         }).invoke())
        //             return retVal
        //         return "bar"
        //     }
        if (closestReturnTargetSymbol == null) compilationException("Return target stack is empty!", expression)
        val actualReturnTargetSymbol = wrappedInIIFE[closestReturnTargetSymbol] ?: closestReturnTargetSymbol
        return context.createIrBuilder(actualReturnTargetSymbol).buildStatement(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
            val returnStatement = if (closestReturnTargetSymbol.owner.hasNonLocalReturns())
                irReturnTrue()
            else
                variablesForReturnTargets[actualReturnTargetSymbol]?.let {
                    irReturn(irGet(it))
                } ?: irReturnUnit()
            irIfThen(iife, returnStatement)
        }
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (declaration.origin == JsLoweredDeclarationOrigin.INLINE_FUNCTION_IIFE) return super.visitFunctionNew(declaration)
        returnTargetSymbolStack.push(declaration.symbol)
        val transformed = super.visitFunctionNew(declaration)
        returnTargetSymbolStack.pop()

        variablesForReturnTargets[declaration.symbol]?.let {
            declaration.body?.prependStatement(it)
        }

        return transformed
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        expression.transformChildrenVoid()

        val targetSymbol = expression.returnTargetSymbol

        val originalFunction = targetSymbol.owner.originalFunction
            ?: compilationException("Return target does not have a corresponding function", targetSymbol.owner)

        if (originalFunction.origin == JsLoweredDeclarationOrigin.INLINE_FUNCTION_IIFE)
            return expression

        val handleNonLocalReturnFromIIFE = wrapInIIFE && targetSymbol != returnTargetSymbolStack.peek()

        if (handleNonLocalReturnFromIIFE && !currentBlockHasNonLocalReturns) {
            for (returnTarget in returnTargetSymbolStack.asReversed()) {
                if (returnTarget == targetSymbol) break
                returnableBlocksWithNonLocalReturns.add(
                    returnTarget.safeAs<IrReturnableBlockSymbol>() ?: compilationException(
                        "returnTargetSymbolStack does not contain returnable blocks",
                        expression
                    )
                )
            }
        }

        if (targetSymbol !is IrReturnableBlockSymbol && !handleNonLocalReturnFromIIFE) return expression

        val iifeSymbol = wrappedInIIFE[targetSymbol]

        val variable = variablesForReturnTargets.getOrPut(targetSymbol) {
            currentScope!!.scope.createTmpVariable(
                originalFunction.returnType,
                nameHint = buildString {
                    collectNamesForLambda(originalFunction)
                    append("\$ret")
                },
                isMutable = true
            )
        }

        return context.createIrBuilder(iifeSymbol ?: targetSymbol).irReturn(
            context.createIrBuilder(iifeSymbol ?: targetSymbol).irComposite {
                +at(expression).irSet(variable.symbol, expression.value)
                if (handleNonLocalReturnFromIIFE)
                    +irTrue()
                else
                    +irUnit()
            }
        )
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        if (!wrappingInIifeEnabled) return super.visitGetValue(expression)
        val currentReturnableBlockSymbol = returnTargetSymbolStack.peek() as? IrReturnableBlockSymbol
        if (currentReturnableBlockSymbol != null && expression.symbol.owner.isDispatchReceiver) {
            returnableBlocksWithCapturedThis[currentReturnableBlockSymbol] = expression.symbol
        }
        return super.visitGetValue(expression)
    }

    private val wrappingInIifeEnabled: Boolean
        get() = true

    /**
     * For a more pleasant debugging experience it makes sense to wrap inlined functions into immediately invoked function expressions
     * (IIFE), so that Step Over and Step Out actions could work properly.
     */
    private val wrapInIIFE: Boolean
        get() = wrappingInIifeEnabled && !isInlineOnlyContext

    private val isInlineOnlyContext: Boolean
        get() = returnTargetSymbolStack.any {
            it.owner.originalFunction?.hasAnnotation(inlineOnlyFqName) ?: false
        }

    private val currentBlockHasNonLocalReturns: Boolean
        get() = returnTargetSymbolStack.peek()?.safeAs<IrReturnableBlockSymbol>()?.let(returnableBlocksWithNonLocalReturns::contains)
            ?: false
}

private val IrReturnTarget.originalFunction: IrFunction?
    get() = when (this) {
        is IrReturnableBlock -> inlineFunctionSymbol?.owner
        is IrFunction -> this
        else -> null
    }

private val inlineOnlyFqName = FqName("kotlin.internal.InlineOnly")
