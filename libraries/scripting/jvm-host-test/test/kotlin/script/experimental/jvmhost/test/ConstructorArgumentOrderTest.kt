/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.script.experimental.jvmhost.test

import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.junit.Test
import kotlin.script.experimental.api.*
import kotlin.test.assertTrue

class ConstructorArgumentsOrderTest {

    @Test
    fun testScriptWithProvidedProperties() {
        val res = evalString<ScriptWithProvidedProperties>("""println(providedString)""") {
            providedProperties("providedString" to "Hello Provided!")
        }

        assertTrue(
            res is ResultWithDiagnostics.Success,
            "test failed:\n  ${res.render()}"
        )
    }

    @Test
    fun testScriptWithImplicitReceiver() {
        val res = evalString<ScriptWithImplicitReceiver>("""println(receiverString)""") {
            implicitReceivers(ImplicitReceiverClass("Hello Receiver!"))
        }

        assertTrue(
            res is ResultWithDiagnostics.Success,
            "test failed:\n  ${res.render()}"
        )
    }

    @Test
    fun testKt53947_ScriptWithImplicitReceiverAndCapturing() {
        // Reproducing (a bit extended) scenario from KT-53947: without the fix, in the presence of the implicit receiver
        // of the same type as the receiver in the `apply` function body, the lowering was incorrectly substituting
        // the correct receiver with the accessor to the implicit one
        val res = evalString<ScriptWithImplicitReceiver>(
            """
                import kotlin.script.experimental.jvmhost.test.ImplicitReceiverClass
                
                val x = "Ok"
                
                class C1 {
                    val y = x + "."
                }
                
                class C2 {
                    fun apply(receiver: ImplicitReceiverClass): String =
                        "--" + receiver.receiverString
                }
                
                C2().apply(ImplicitReceiverClass(C1().y))
            """.trimIndent()
        ) {
            implicitReceivers(ImplicitReceiverClass("Not Ok."))
        }

        assertTrue(
            res.safeAs<ResultWithDiagnostics.Success<EvaluationResult>>()?.value?.returnValue?.safeAs<ResultValue.Value>()?.value == "--Ok.",
            "test failed:\n  ${res.render()}"
        )
    }

    @Test
    fun testScriptWithBoth() {
        val res = evalString<ScriptWithBoth>("""println(providedString + receiverString)""") {
            providedProperties("providedString" to "Hello")
            implicitReceivers(ImplicitReceiverClass(" Both!"))
        }

        assertTrue(
            res is ResultWithDiagnostics.Success,
            "test failed:\n  ${res.render()}"
        )
    }

    private fun ResultWithDiagnostics<EvaluationResult>.render() =
        reports.joinToString("\n  ") { it.message + if (it.exception == null) "" else ": ${it.exception!!.printStackTrace()}" }
}
