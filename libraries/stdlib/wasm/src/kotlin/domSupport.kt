/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

external class Dynamic

fun Any.asDynamic(): Dynamic = this as Dynamic

@JsFun("(s) => eval(s)")
external fun js(code: String): Dynamic

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetBoolean(obj: Dynamic, index: String, value: Boolean)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetByte(obj: Dynamic, index: String, value: Byte)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetShort(obj: Dynamic, index: String, value: Short)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetChar(obj: Dynamic, index: String, value: Char)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetInt(obj: Dynamic, index: String, value: Int)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetLong(obj: Dynamic, index: String, value: Long)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetFloat(obj: Dynamic, index: String, value: Float)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetDouble(obj: Dynamic, index: String, value: Double)

@JsFun("(obj, index, value) => obj[index] = value")
external fun dynamicSetAny(obj: Dynamic, index: String, value: Any?)


@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetBoolean(obj: Dynamic, index: String): Boolean

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetByte(obj: Dynamic, index: String): Byte

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetShort(obj: Dynamic, index: String): Short

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetChar(obj: Dynamic, index: String): Char

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetInt(obj: Dynamic, index: String): Int

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetLong(obj: Dynamic, index: String): Long

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetFloat(obj: Dynamic, index: String): Float

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetDouble(obj: Dynamic, index: String): Double

@JsFun("(obj, index, value) => obj[index]")
external fun dynamicGetAny(obj: Dynamic, index: String): Any?

operator fun Dynamic.set(index: String, value: Any?) {
    when (value) {
        is Boolean -> dynamicSetBoolean(this, index, value)
        is Byte -> dynamicSetByte(this, index, value)
        is Short -> dynamicSetShort(this, index, value)
        is Char -> dynamicSetChar(this, index, value)
        is Int -> dynamicSetInt(this, index, value)
        is Long -> dynamicSetLong(this, index, value)
        is Float -> dynamicSetFloat(this, index, value)
        is Double -> dynamicSetDouble(this, index, value)
        else -> dynamicSetAny(this, index, value)
    }
}

operator fun Dynamic.set(index: Int, value: Any?) {
    this[index.toString()] = value
}

inline operator fun <reified T> Dynamic.get(index: String): T {
    return when (T::class) {
        Boolean::class -> dynamicGetBoolean(this, index) as T
        Byte::class -> dynamicGetByte(this, index) as T
        Short::class -> dynamicGetShort(this, index) as T
        Char::class -> dynamicGetChar(this, index) as T
        Int::class -> dynamicGetInt(this, index) as T
        Long::class -> dynamicGetLong(this, index) as T
        Float::class -> dynamicGetFloat(this, index) as T
        Double::class -> dynamicGetDouble(this, index) as T
        else -> dynamicGetAny(this, index) as T
    }
}

inline operator fun <reified T> Dynamic.get(index: Int): T {
    return this[index.toString()]
}


class Promise<T>

public external interface ItemArrayLike<out T> {
    val length: Int
    fun item(index: Int): T?
}

/**
 * Returns the view of this `ItemArrayLike<T>` collection as `List<T>`
 */
public fun <T> ItemArrayLike<T>.asList(): List<T> = object : AbstractList<T>() {
    override val size: Int get() = this@asList.length

    override fun get(index: Int): T = when (index) {
        in 0..lastIndex -> this@asList.item(index) as T
        else -> throw IndexOutOfBoundsException("index $index is not in range [0..$lastIndex]")
    }
}

@JsFun("(x) => x")
public external fun <T> unsafeCastJs(x: String): Dynamic
public fun <T> String.unsafeCast(): T = unsafeCastJs<T>(this) as T

@JsFun("(x) => x")
public external fun <T> unsafeCastJs(x: Boolean): Dynamic
public fun <T> Boolean.unsafeCast(): T = unsafeCastJs<T>(this) as T

public fun <T> Nothing?.unsafeCast(): Dynamic? = null

public val undefined: Nothing? = null