// TARGET_BACKEND: JVM_IR
// !LANGUAGE: +ReferencesToSyntheticJavaProperties
// WITH_STDLIB
// WITH_REFLECT

// FILE: J.java

public class J {
    private String stringProperty;

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String value) {
        stringProperty = value;
    }
}

// FILE: main.kt

import kotlin.reflect.*
import kotlin.test.*
import kotlin.jvm.KotlinReflectionNotSupportedError

fun box(): String {
    val stringProperty = J::stringProperty

    try {
        stringProperty.visibility
        return "Fail"
    } catch (e: KotlinReflectionNotSupportedError) {
        assertEquals("Kotlin reflection is not yet supported for synthetic Java properties", e.message)
        return "OK"
    }
}