// DONT_TARGET_EXACT_BACKEND: JS
// GENERATE_INLINE_ANONYMOUS_FUNCTIONS

inline fun foo(l: () -> Unit) { l() }
inline fun bar(l: () -> Unit) { l() }

inline fun baz(l: () -> String) = l()

fun noninline(l: () -> String) = l()

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.InlineOnly
inline fun inlineOnly(l: () -> String) = l()

fun test1(a: Boolean) = baz {
    foo {
        val s = "O"
        val localFun: () -> String = { s }
        if (a)
            return@baz localFun()
        else
            return@baz "K"
    }
    "Fail test1"
}

fun test2(): String {
    foo {
        bar {
            return "OK"
        }
    }
    return "Fail test2"
}

fun test3(): String {
    foo {
        bar {
            return@foo;
        }
        return "Fail"
    }
    return "OK"
}

class A(val a: Int) {

    fun test4() = baz { "$a" }

    inner class B {
        fun test5() = baz { "$a" }

        fun test6() = noninline {
            baz { "$a" }
        }
    }

    fun test7() = noninline {
        baz { "$a" }
    }

    fun test9() = baz {
        inlineOnly {
            "$a"
        }
    }
}

fun test8(a: Long = 5.seconds) = a.toString()

inline val Number.seconds: Long get() = this.toLong()

fun box(): String {
    assertEquals(test1(true) + test1(false), "OK")
    assertEquals(test2(), "OK")
    assertEquals(test3(), "OK")
    assertEquals(A(1).test4(), "1")
    assertEquals(A(2).B().test5(), "2")
    assertEquals(A(3).B().test6(), "3")
    assertEquals(A(4).test7(), "4")
    assertEquals(test8(), "5")
    assertEquals(A(6).test9(), "6")
    return "OK"
}
