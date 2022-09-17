// FILE: externals.js

class A {

}
class B extends A {

}

// FILE: externals.kt

import kotlinx.browser.*
import org.w3c.dom.*
import kotlinx.dom.*

//external class A
//class B
//
//@JsFun("(x) => x")
//external fun f(x: B): B
//
//
//fun lolkek1(x: B): A {
//    return x as A
//}
//
//fun lolkek() {
//    println(lolkek1(B()))
//}

external open class A
external class B : A

@JsFun("() => new A()")
external fun f(): A


//fun lolkek() {
//    val x: Any = f()
//    x as B
//}

fun box(): String {

//    lolkek()

//    with(x as B) {
//        println(this)
//    }

    document!!.body!!.appendElement("div") {
        with(this as HTMLDivElement) {
            innerText = "I HATE DUKAT!"
        }
    }
//    lolkek()
//

    return "OK"
}