// TARGET_BACKEND: JVM_IR
// MODULE: m1
// FILE: A.kt

open class A {
    internal open fun foo() : Int = 1
}

// MODULE: m2(m1)
// FILE: B.kt

class B : A() {
    fun foo() : String = "OK"
}
