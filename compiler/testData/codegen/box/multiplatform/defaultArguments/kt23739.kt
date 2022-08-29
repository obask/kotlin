// !LANGUAGE: +MultiPlatformProjects
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: default argument mapping in MPP isn't designed yet
// IGNORE_BACKEND_FIR_WITH_IR_LINKER: JVM_IR
// FILE: common.kt

// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES
// A LOT OF LINES

expect inline fun <T> get(p: String = "OK"): String

// FILE: platform.kt

actual inline fun <T> get(p: String): String {
    return p
}

fun box(): String {
    return get<String>()
}
