// IGNORE_BACKEND: JVM, JS_IR, JS_IR_ES6
// WITH_REFLECT

import kotlin.reflect.typeOf
import kotlin.test.assertEquals

inline fun <reified T> foo() =
    typeOf<Array<Array<T>?>>()

fun box(): String {
    assertEquals(typeOf<Array<Array<String>?>>(), foo<String>())
    return "OK"
}
