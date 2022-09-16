// EXPECTED_REACHABLE_NODES: 1265
// ES_MODULES
// SKIP_MINIFICATION
// SKIP_DCE_DRIVEN
// SKIP_NODE_JS

// See KT-43783

// MODULE: nestedObjectExport
// FILE: lib.kt
@file:JsExport

class Abc {
    companion object AbcCompanion {
        fun xyz(): String = "Companion object method OK"

        val prop: String
            get() = "Companion object property OK"
    }
}

class Foo {
    companion object {
        fun xyz(): String = "Companion object method OK"

        val prop: String
            get() = "Companion object property OK"
    }
}

sealed class MyEnum(val name: String) {
    object A: MyEnum("A")
    object B: MyEnum("B")
    object C: MyEnum("C")
}

object MyObject {
    object A {
        fun valueA() = "OK"
    }
    object B {
        fun valueB() = "OK"
    }
    object C {
        fun valueC() = "OK"
    }
}

// FILE: main.mjs
// ENTRY_ES_MODULE
import { Foo, Abc, MyEnum, MyObject } from "./main.mjs"

function box() {
    if (Abc.Companion.xyz() != 'Companion object method OK') return 'companion object function failure';
    if (Abc.Companion.prop != 'Companion object property OK') return 'companion object property failure';

    if (Foo.Companion.xyz() != 'Companion object method OK') return 'companion object function failure';
    if (Foo.Companion.prop != 'Companion object property OK') return 'companion object property failure';

    if (MyEnum.A.name != 'A') return 'MyEnum.A failure';
    if (MyEnum.B.name != 'B') return 'MyEnum.B failure';
    if (MyEnum.C.name != 'C') return 'MyEnum.C failure';

    if (MyObject.A.valueA() != "OK") return 'MyObject.A failure';
    if (MyObject.B.valueB() != "OK") return 'MyObject.B failure';
    if (MyObject.C.valueC() != "OK") return 'MyObject.C failure';

    return 'OK';
}
