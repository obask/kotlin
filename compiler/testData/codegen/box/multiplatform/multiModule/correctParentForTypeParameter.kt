// IGNORE_BACKEND_FIR: JVM_IR
// IGNORE_BACKEND: JS, JS_IR, WASM, NATIVE
// !LANGUAGE: +MultiPlatformProjects
// MODULE: lib
// FILE: lib.kt

fun transform(x: String, f: (String) -> String): String {
    return f(x) + "K"
}

// MODULE: lib2()()(lib)
// TARGET_BACKEND: JVM
// FILE: main.kt

fun box() = transform("") { "O" }