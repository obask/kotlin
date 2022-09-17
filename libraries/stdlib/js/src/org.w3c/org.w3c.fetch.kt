/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// NOTE: THIS FILE IS AUTO-GENERATED, DO NOT EDIT!
// See github.com/kotlin/dukat for details

package org.w3c.fetch

import kotlin.js.*
import org.khronos.webgl.*
import org.w3c.files.*
import org.w3c.xhr.*

/**
 * Exposes the JavaScript [Headers](https://developer.mozilla.org/en/docs/Web/API/Headers) to Kotlin
 */
public external open class Headers(init: Dynamic? = definedExternally) {
    fun append(name: String, value: String)
    fun delete(name: String)
    fun get(name: String): String?
    fun has(name: String): Boolean
    fun set(name: String, value: String)
}

/**
 * Exposes the JavaScript [Body](https://developer.mozilla.org/en/docs/Web/API/Body) to Kotlin
 */
public external interface Body {
    val bodyUsed: Boolean
    fun arrayBuffer(): Promise<ArrayBuffer>
    fun blob(): Promise<Blob>
    fun formData(): Promise<FormData>
    fun json(): Promise<Any?>
    fun text(): Promise<String>
}

/**
 * Exposes the JavaScript [Request](https://developer.mozilla.org/en/docs/Web/API/Request) to Kotlin
 */
public external open class Request(input: Dynamic?, init: RequestInit = definedExternally) : Body {
    open val method: String
    open val url: String
    open val headers: Headers
    open val type: RequestType
    open val destination: RequestDestination
    open val referrer: String
    open val referrerPolicy: Dynamic?
    open val mode: RequestMode
    open val credentials: RequestCredentials
    open val cache: RequestCache
    open val redirect: RequestRedirect
    open val integrity: String
    open val keepalive: Boolean
    override val bodyUsed: Boolean
    fun clone(): Request
    override fun arrayBuffer(): Promise<ArrayBuffer>
    override fun blob(): Promise<Blob>
    override fun formData(): Promise<FormData>
    override fun json(): Promise<Any?>
    override fun text(): Promise<String>
}

public external interface RequestInit {
    var method: String?
        get() = definedExternally
        set(value) = definedExternally
    var headers: Dynamic?
        get() = definedExternally
        set(value) = definedExternally
    var body: Dynamic?
        get() = definedExternally
        set(value) = definedExternally
    var referrer: String?
        get() = definedExternally
        set(value) = definedExternally
    var referrerPolicy: Dynamic?
        get() = definedExternally
        set(value) = definedExternally
    var mode: RequestMode?
        get() = definedExternally
        set(value) = definedExternally
    var credentials: RequestCredentials?
        get() = definedExternally
        set(value) = definedExternally
    var cache: RequestCache?
        get() = definedExternally
        set(value) = definedExternally
    var redirect: RequestRedirect?
        get() = definedExternally
        set(value) = definedExternally
    var integrity: String?
        get() = definedExternally
        set(value) = definedExternally
    var keepalive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var window: Any?
        get() = definedExternally
        set(value) = definedExternally
}

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@kotlin.internal.InlineOnly
public inline fun RequestInit(method: String? = undefined, headers: Dynamic? = undefined.unsafeCast<Dynamic?>(), body: Dynamic? = undefined.unsafeCast<Dynamic?>(), referrer: String? = undefined, referrerPolicy: Dynamic? = undefined.unsafeCast<Dynamic?>(), mode: RequestMode? = undefined, credentials: RequestCredentials? = undefined, cache: RequestCache? = undefined, redirect: RequestRedirect? = undefined, integrity: String? = undefined, keepalive: Boolean? = undefined, window: Any? = undefined): RequestInit {
    val o = js("({})")
    o["method"] = method
    o["headers"] = headers
    o["body"] = body
    o["referrer"] = referrer
    o["referrerPolicy"] = referrerPolicy
    o["mode"] = mode
    o["credentials"] = credentials
    o["cache"] = cache
    o["redirect"] = redirect
    o["integrity"] = integrity
    o["keepalive"] = keepalive
    o["window"] = window
    return o as RequestInit
}

/**
 * Exposes the JavaScript [Response](https://developer.mozilla.org/en/docs/Web/API/Response) to Kotlin
 */
public external open class Response(body: Dynamic? = definedExternally, init: ResponseInit = definedExternally) : Body {
    open val type: ResponseType
    open val url: String
    open val redirected: Boolean
    open val status: Short
    open val ok: Boolean
    open val statusText: String
    open val headers: Headers
    open val body: Dynamic?
    open val trailer: Promise<Headers>
    override val bodyUsed: Boolean
    fun clone(): Response
    override fun arrayBuffer(): Promise<ArrayBuffer>
    override fun blob(): Promise<Blob>
    override fun formData(): Promise<FormData>
    override fun json(): Promise<Any?>
    override fun text(): Promise<String>

    companion object {
        fun error(): Response
        fun redirect(url: String, status: Short = definedExternally): Response
    }
}

public external interface ResponseInit {
    var status: Short? /* = 200 */
        get() = definedExternally
        set(value) = definedExternally
    var statusText: String? /* = "OK" */
        get() = definedExternally
        set(value) = definedExternally
    var headers: Dynamic?
        get() = definedExternally
        set(value) = definedExternally
}

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@kotlin.internal.InlineOnly
public inline fun ResponseInit(status: Short? = 200, statusText: String? = "OK", headers: Dynamic? = undefined.unsafeCast<Dynamic?>()): ResponseInit {
    val o = js("({})")
    o["status"] = status
    o["statusText"] = statusText
    o["headers"] = headers
    return o as ResponseInit
}

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface RequestType {
    companion object
}

public inline val RequestType.Companion.EMPTY: RequestType get() = "".unsafeCast<RequestType>()

public inline val RequestType.Companion.AUDIO: RequestType get() = "audio".unsafeCast<RequestType>()

public inline val RequestType.Companion.FONT: RequestType get() = "font".unsafeCast<RequestType>()

public inline val RequestType.Companion.IMAGE: RequestType get() = "image".unsafeCast<RequestType>()

public inline val RequestType.Companion.SCRIPT: RequestType get() = "script".unsafeCast<RequestType>()

public inline val RequestType.Companion.STYLE: RequestType get() = "style".unsafeCast<RequestType>()

public inline val RequestType.Companion.TRACK: RequestType get() = "track".unsafeCast<RequestType>()

public inline val RequestType.Companion.VIDEO: RequestType get() = "video".unsafeCast<RequestType>()

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface RequestDestination {
    companion object
}

public inline val RequestDestination.Companion.EMPTY: RequestDestination get() = "".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.DOCUMENT: RequestDestination get() = "document".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.EMBED: RequestDestination get() = "embed".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.FONT: RequestDestination get() = "font".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.IMAGE: RequestDestination get() = "image".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.MANIFEST: RequestDestination get() = "manifest".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.MEDIA: RequestDestination get() = "media".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.OBJECT: RequestDestination get() = "object".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.REPORT: RequestDestination get() = "report".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.SCRIPT: RequestDestination get() = "script".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.SERVICEWORKER: RequestDestination get() = "serviceworker".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.SHAREDWORKER: RequestDestination get() = "sharedworker".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.STYLE: RequestDestination get() = "style".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.WORKER: RequestDestination get() = "worker".unsafeCast<RequestDestination>()

public inline val RequestDestination.Companion.XSLT: RequestDestination get() = "xslt".unsafeCast<RequestDestination>()

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface RequestMode {
    companion object
}

public inline val RequestMode.Companion.NAVIGATE: RequestMode get() = "navigate".unsafeCast<RequestMode>()

public inline val RequestMode.Companion.SAME_ORIGIN: RequestMode get() = "same-origin".unsafeCast<RequestMode>()

public inline val RequestMode.Companion.NO_CORS: RequestMode get() = "no-cors".unsafeCast<RequestMode>()

public inline val RequestMode.Companion.CORS: RequestMode get() = "cors".unsafeCast<RequestMode>()

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface RequestCredentials {
    companion object
}

public inline val RequestCredentials.Companion.OMIT: RequestCredentials get() = "omit".unsafeCast<RequestCredentials>()

public inline val RequestCredentials.Companion.SAME_ORIGIN: RequestCredentials get() = "same-origin".unsafeCast<RequestCredentials>()

public inline val RequestCredentials.Companion.INCLUDE: RequestCredentials get() = "include".unsafeCast<RequestCredentials>()

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface RequestCache {
    companion object
}

public inline val RequestCache.Companion.DEFAULT: RequestCache get() = "default".unsafeCast<RequestCache>()

public inline val RequestCache.Companion.NO_STORE: RequestCache get() = "no-store".unsafeCast<RequestCache>()

public inline val RequestCache.Companion.RELOAD: RequestCache get() = "reload".unsafeCast<RequestCache>()

public inline val RequestCache.Companion.NO_CACHE: RequestCache get() = "no-cache".unsafeCast<RequestCache>()

public inline val RequestCache.Companion.FORCE_CACHE: RequestCache get() = "force-cache".unsafeCast<RequestCache>()

public inline val RequestCache.Companion.ONLY_IF_CACHED: RequestCache get() = "only-if-cached".unsafeCast<RequestCache>()

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface RequestRedirect {
    companion object
}

public inline val RequestRedirect.Companion.FOLLOW: RequestRedirect get() = "follow".unsafeCast<RequestRedirect>()

public inline val RequestRedirect.Companion.ERROR: RequestRedirect get() = "error".unsafeCast<RequestRedirect>()

public inline val RequestRedirect.Companion.MANUAL: RequestRedirect get() = "manual".unsafeCast<RequestRedirect>()

/* please, don't implement this interface! */
@JsName("null")
@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
public external interface ResponseType {
    companion object
}

public inline val ResponseType.Companion.BASIC: ResponseType get() = "basic".unsafeCast<ResponseType>()

public inline val ResponseType.Companion.CORS: ResponseType get() = "cors".unsafeCast<ResponseType>()

public inline val ResponseType.Companion.DEFAULT: ResponseType get() = "default".unsafeCast<ResponseType>()

public inline val ResponseType.Companion.ERROR: ResponseType get() = "error".unsafeCast<ResponseType>()

public inline val ResponseType.Companion.OPAQUE: ResponseType get() = "opaque".unsafeCast<ResponseType>()

public inline val ResponseType.Companion.OPAQUEREDIRECT: ResponseType get() = "opaqueredirect".unsafeCast<ResponseType>()