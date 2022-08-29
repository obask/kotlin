// TARGET_BACKEND: JVM
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: don't support legacy feature (see https://youtrack.jetbrains.com/issue/KT-37591). UNRESOLVED_REFERENCE at '+'
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR
// WITH_STDLIB
// MODULE: lib
// FILE: lib.kt

enum class E(val value: String) {
    OK("K");

    companion object {
        @JvmField
        val OK = "O"
    }
}

// MODULE: main(lib)
// FILE: main.kt
fun box() = E.OK + E.OK.value
