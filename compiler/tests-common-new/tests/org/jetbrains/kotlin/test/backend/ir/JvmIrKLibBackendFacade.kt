/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.KtPsiSourceFile
import org.jetbrains.kotlin.backend.common.BackendException
import org.jetbrains.kotlin.backend.jvm.MultifileFacadeFileEntry
import org.jetbrains.kotlin.backend.jvm.lower.getFileClassInfoFromIrFile
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.backend.classic.JavaCompilerFacade
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.compilerConfigurationProvider
import org.jetbrains.kotlin.test.services.dependencyProvider

class JvmIrKLibBackendFacade(
    val testServices: TestServices,
) : AbstractTestFacade<KLibArtifact, BinaryArtifacts.Jvm>() {
    private val javaCompilerFacade = JavaCompilerFacade(testServices)

    override val inputKind = KLibKinds.KLib
    override val outputKind = ArtifactKinds.Jvm

    override fun shouldRunAnalysis(module: TestModule): Boolean {
        return module.binaryKind == outputKind
    }

    override fun transform(module: TestModule, inputArtifact: KLibArtifact): BinaryArtifacts.Jvm? {
        require(inputArtifact is KLibArtifact.JvmIrKLibArtifact) {
            "JvmIrKLibBackendFacade expects KLibArtifact.JvmIrKLibArtifact as input"
        }

        link(inputArtifact, module)

        val state = inputArtifact.state
        try {
            inputArtifact.codegenFactory.generateModule(state, inputArtifact.backendInput)
        } catch (e: BackendException) {
            if (CodegenTestDirectives.IGNORE_ERRORS in module.directives) {
                return null
            }
            throw e
        }
        state.factory.done()
        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)
        javaCompilerFacade.compileJavaFiles(module, configuration, state.factory)

        fun sourceFileInfos(irFile: IrFile, allowNestedMultifileFacades: Boolean): List<SourceFileInfo> =
            when (val fileEntry = irFile.fileEntry) {
                is PsiIrFileEntry -> {
                    listOf(
                        SourceFileInfo(
                            KtPsiSourceFile(fileEntry.psiFile),
                            JvmFileClassUtil.getFileClassInfoNoResolve(fileEntry.psiFile as KtFile)
                        )
                    )
                }
                is NaiveSourceBasedFileEntryImpl -> {
                    val sourceFile = inputArtifact.sourceFiles.find { it.path == fileEntry.name }
                    if (sourceFile == null) emptyList() // synthetic files, like CoroutineHelpers.kt, are ignored here
                    else listOf(SourceFileInfo(sourceFile, getFileClassInfoFromIrFile(irFile, sourceFile.name)))
                }
                is MultifileFacadeFileEntry -> {
                    if (!allowNestedMultifileFacades) error("nested multi-file facades are not allowed")
                    else fileEntry.partFiles.flatMap { sourceFileInfos(it, allowNestedMultifileFacades = false) }
                }
                else -> {
                    error("unknown kind of file entry: $fileEntry")
                }
            }

        return BinaryArtifacts.Jvm(
            state.factory,
            inputArtifact.irModuleFragment.files.flatMap {
                sourceFileInfos(it, allowNestedMultifileFacades = true)
            }
        )
    }

    private fun link(inputArtifact: KLibArtifact, module: TestModule) {
        if (module.dependsOnDependencies.isEmpty()) return
        val mainFile = inputArtifact.irModuleFragment.files.lastOrNull() ?: return

        val dependencyProvider = testServices.dependencyProvider
        val replacementMap = mutableMapOf<IdSignature, IrSymbol>()
        val expectMembers = mutableSetOf<IdSignature>()

        fun fillReplacementMap(declaration: IrDeclaration, root: IrFile?) {
            if (declaration is IrClass && declaration.origin == IrDeclarationOrigin.FILE_CLASS) {
                for (subDeclaration in declaration.declarations) {
                    fillReplacementMap(subDeclaration, root)
                }
            }

            val signature = declaration.symbol.signature as? IdSignature.CommonSignature ?: return

            if (declaration is IrFunction && declaration.isExpect ||
                declaration is IrProperty && declaration.isExpect
            ) {
                expectMembers.add(IdSignature.CommonSignature(signature.packageFqName, signature.declarationFqName, signature.id, 0))
            } else {
                if (signature.id != null) {
                    replacementMap[signature] = declaration.symbol
                }

                if (root != null) {
                    declaration.parent = root
                    root.declarations.add(declaration)
                }
            }
        }

        for (dependency in module.dependsOnDependencies) {
            val testModule = dependencyProvider.getTestModule(dependency.moduleName)
            val artifact = dependencyProvider.getArtifact(testModule, KLibKinds.KLib)

            for (file in artifact.irModuleFragment.files) {
                for (declaration in file.declarations) {
                    fillReplacementMap(declaration, mainFile)
                }
            }
        }

        for (file in inputArtifact.irModuleFragment.files) {
            for (declaration in file.declarations) {
                if (declaration is IrFunction || declaration is IrProperty) {
                    val signature = declaration.symbol.signature ?: continue
                    if (expectMembers.contains(signature)) {
                        replacementMap[signature] = declaration.symbol
                    }
                }
            }
        }

        val callReplacer = object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                val keySignature = when (val signature = expression.symbol.signature) {
                    is IdSignature.AccessorSignature -> {
                        val propertySignature = signature.propertySignature as? IdSignature.CommonSignature
                        if (propertySignature != null) {
                            IdSignature.CommonSignature(
                                propertySignature.packageFqName,
                                propertySignature.declarationFqName, propertySignature.id, 0
                            )
                        } else {
                            null
                        }
                    }
                    is IdSignature.CommonSignature -> {
                        if (expression.symbol.owner.isExpect)
                            IdSignature.CommonSignature(signature.packageFqName, signature.declarationFqName, signature.id, 0)
                        else
                            signature
                    }
                    else -> {
                        null
                    }
                }
                if (keySignature != null) {
                    val replacement = replacementMap[keySignature]
                    val newSymbol = if (replacement is IrSimpleFunctionSymbol) {
                        replacement
                    } else if (replacement is IrPropertySymbol) {
                        val owner = replacement.owner
                        if (expression.origin == IrStatementOrigin.GET_PROPERTY) {
                            owner.getter!!.symbol
                        } else {
                            owner.setter!!.symbol
                        }
                    } else {
                        null
                    }

                    if (newSymbol != null) {
                        val result = IrCallImpl(
                            expression.startOffset,
                            expression.endOffset,
                            expression.type,
                            newSymbol,
                            expression.typeArgumentsCount,
                            expression.valueArgumentsCount,
                            expression.origin,
                            expression.superQualifierSymbol
                        )
                        result.contextReceiversCount = expression.contextReceiversCount
                        result.extensionReceiver = expression.extensionReceiver?.let { visitExpression(it) }
                        result.dispatchReceiver = expression.dispatchReceiver?.let { visitExpression(it) }
                        for (index in 0 until expression.valueArgumentsCount) {
                            val valueArgument = expression.getValueArgument(index)?.let { visitExpression(it) }
                            result.putValueArgument(index, valueArgument)
                        }
                        for (index in 0 until expression.typeArgumentsCount) {
                            val typeArgument = expression.getTypeArgument(index)
                            result.putTypeArgument(index, typeArgument)
                        }
                        return result
                    }
                }
                return super.visitCall(expression)
            }
        }

        for (declaration in mainFile.declarations) {
            declaration.transformChildrenVoid(callReplacer)
        }
    }
}