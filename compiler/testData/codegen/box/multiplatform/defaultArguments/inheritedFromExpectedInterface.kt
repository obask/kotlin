// !LANGUAGE: +MultiPlatformProjects
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: default argument mapping in MPP isn't designed yet
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR

// FILE: lib.kt
expect interface I {
    fun f(p: String = "OK"): String
}

// FILE: main.kt
actual interface I {
    actual fun f(p: String): String
}

class Impl : I {
    override fun f(p: String) = p
}

fun box() = Impl().f()
