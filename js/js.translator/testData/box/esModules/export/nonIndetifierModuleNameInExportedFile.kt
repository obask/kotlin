// EXPECTED_REACHABLE_NODES: 1270
// ES_MODULES
// SKIP_MINIFICATION
// SKIP_NODE_JS

// MODULE: non_identifier_module_name
// FILE: lib.kt
@file:JsExport

@JsName("foo")
public fun foo(k: String): String = "O$k"

// FILE: main.mjs
// ENTRY_ES_MODULE
import { foo } from "./nonIndetifierModuleNameInExportedFile-non_identifier_module_name_v5.mjs";

export function box() {
    return foo("K")
}
