// LANGUAGE: -ProhibitSimplificationOfNonTrivialConstBooleanExpressions
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: don't support legacy feature
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR
fun box() : String = when (true) {
    ((true)) -> "OK"
    (1 == 2) -> "Not ok"
}
