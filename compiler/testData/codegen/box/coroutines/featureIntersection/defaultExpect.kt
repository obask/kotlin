// !LANGUAGE: +MultiPlatformProjects
// WITH_STDLIB
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: expect/actual in the same module (ACTUAL_WITHOUT_EXPECT)
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR

import kotlin.coroutines.*

var res = 0L

expect suspend fun withLimit(limit: Long = 42L)

actual suspend fun withLimit(limit: Long) {
    res = limit
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(Continuation(EmptyCoroutineContext) {
        it.getOrThrow()
    })
}

fun box(): String {
    builder {
        withLimit()
    }
    return if (res == 42L) "OK" else "FAIL $res"
}
