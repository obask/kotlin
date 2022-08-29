// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: result.getMethod OK in FE1.0, unresolved in FIR
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR

class C(val value: String) {
    fun getField() = value
    fun getMethod() {}
}

fun foo(): Any {
    var result: Any = ""
    result = C("OK")
    try {
        result = result.getField()
    } catch (e: Exception) {
        result.getMethod()
    }
    return result
}


fun box() = foo().toString()
