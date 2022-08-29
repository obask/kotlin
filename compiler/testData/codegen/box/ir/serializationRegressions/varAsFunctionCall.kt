// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: ARGUMENT_TYPE_MISMATCH at ok.z(fail), actual String, expected T
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR
// MODULE: lib
// FILE: l1.kt

val <T : CharSequence> T.z
    get() = { x: T -> this }

// FILE: l2.kt

fun test(ok: String, fail: String) = ok.z(fail)

// MODULE: main(lib)
// FILE: main.kt

fun box() = test("OK", "FAIL")