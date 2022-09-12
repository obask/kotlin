// IGNORE_BACKEND: JS
// RUN_PLAIN_BOX_FUNCTION
// INFER_MAIN_MODULE
// KEEP: A.foo

// MODULE: keep_method
// FILE: lib.kt

class A {
    fun foo(): String {
        return "foo"
    }

    fun bar(): String {
        return "bar"
    }
}

@JsExport
fun bar(): A {
    return A()
}

// FILE: test.js
function box() {
    var a = this["keep_method"].bar()

    if (a.foo_26di_k$() != "foo") return "fail 1"

    return "OK"
}