/** This file is generated by {@link :js:js.test:generateJsExportOnFileTestFilesForTS} task. DO NOT MODIFY MANUALLY */

// CHECK_TYPESCRIPT_DECLARATIONS
// SKIP_MINIFICATION
// SKIP_NODE_JS
// INFER_MAIN_MODULE
// MODULE: JS_TESTS
// MODULE_KIND: ES
// FILE: esm.kt

@file:JsExport

package foo


val value = 10


var variable = 10


class C(val x: Int) {
    fun doubleX() = x * 2
}


object O {
    val value = 10
}


object Parent {
    val value = 10
    class Nested {
        val value = 10
    }
}


fun box(): String = "OK"