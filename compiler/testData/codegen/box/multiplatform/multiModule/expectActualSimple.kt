// IGNORE_BACKEND_FIR: JVM_IR
// IGNORE_BACKEND: JS, JS_IR, WASM, NATIVE
// !LANGUAGE: +MultiPlatformProjects
// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt

expect fun func(): String

expect var prop: String

fun test(): String {
    prop = "K"
    return func() + prop
}

// MODULE: jvm()()(common)
// TARGET_PLATFORM: JVM
// FILE: jvm.kt
actual fun func(): String = "O"

actual var prop: String = "!"

fun box() = test()