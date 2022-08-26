/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.js.test.debugger

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.util.logging.*
import kotlin.coroutines.*

/**
 * A client that fires up a Node.js instance in the inspector mode, and connects to it via websocket,
 * allowing us to communicate with it using [Chrome DevTools protocol](https://chromedevtools.github.io/devtools-protocol/).
 *
 * @param scriptPath the script for Node to run.
 */
class NodeJsInspectorClient(private val scriptPath: String, private val shareNodeJsInstanceBetweenRuns: Boolean) {

    private var onDebuggerEventCallback: (NodeJsInspectorClientContext.(CDPEvent) -> Unit)? = null

    fun <T> run(
        prepareInspector: suspend NodeJsInspectorClientContext.() -> Unit,
        body: suspend NodeJsInspectorClientContext.() -> T
    ): T = runBlocking {
        val context = if (shareNodeJsInstanceBetweenRuns) {
            NodeJsInspectorClientContextImpl.createOrGetThreadLocal {
                runWithContext(this, prepareInspector)
            }
        } else {
            NodeJsInspectorClientContextImpl().apply {
                initializeIfNeeded {
                    runWithContext(this, prepareInspector)
                }
            }
        }
        context.writeln(scriptPath)
        runWithContext(context, body)
    }

    private suspend fun <T> runWithContext(
        context: NodeJsInspectorClientContextImpl,
        block: suspend NodeJsInspectorClientContext.() -> T
    ): T {

        var blockResult: Result<T>? = null
        block.startCoroutine(context, object : Continuation<T> {
            override val context: CoroutineContext
                get() = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                blockResult = result
            }
        })

        context.listenForMessages { message ->
            when (val response = decodeCDPResponse(message) { context.messageContinuations[it]!!.encodingInfo }) {
                is CDPResponse.Event -> onDebuggerEventCallback?.invoke(context, response.event)
                is CDPResponse.MethodInvocationResult -> context.messageContinuations.remove(response.id)!!.continuation.resume(response.result)
                is CDPResponse.Error -> context.messageContinuations[response.id]!!.let { (_, continuation) ->
                    continuation.resumeWithException(
                        IllegalStateException("error ${response.error.code}" + (response.error.message?.let { ": $it" } ?: ""))
                    )
                }
            }
            context.waitingOnPredicate?.let { (predicate, continuation) ->
                if (predicate()) {
                    context.waitingOnPredicate = null
                    continuation.resume(Unit)
                }
            }
            blockResult != null
        }

        return blockResult!!.getOrThrow()
    }

    /**
     * Installs a listener for Chrome DevTools Protocol events.
     */
    fun onEvent(receiveEvent: NodeJsInspectorClientContext.(CDPEvent) -> Unit) {
        onDebuggerEventCallback = receiveEvent
    }
}

private const val NODE_WS_DEBUG_URL_PREFIX = "Debugger listening on ws://"

/**
 * The actual implementation of the Node.js inspector client.
 */
private class NodeJsInspectorClientContextImpl : NodeJsInspectorClientContext, CDPRequestEvaluator {

    companion object {
        private val instanceTL = object : ThreadLocal<NodeJsInspectorClientContextImpl>() {
            override fun initialValue() = NodeJsInspectorClientContextImpl()
            override fun remove() {
                get().release()
            }
        }

        suspend fun createOrGetThreadLocal(init: suspend NodeJsInspectorClientContextImpl.() -> Unit): NodeJsInspectorClientContextImpl =
            instanceTL.get().apply { initializeIfNeeded(init) }
    }

    private var isInitialized = false

    suspend fun initializeIfNeeded(init: suspend NodeJsInspectorClientContextImpl.() -> Unit) {
        if (!isInitialized) {
            logger.fine { "Preparing NodeJS inspector…" }
            isInitialized = true
            this.init()
        }
    }

    private val logger = Logger.getLogger(this::class.java.name)

    private val nodeProcess = ProcessBuilder(
        System.getProperty("javascript.engine.path.NodeJs"),
        "--inspect=0",
        "js/js.tests/test/org/jetbrains/kotlin/js/test/debugger/stepping_test_executor.js",
    ).also {
        logger.fine(it::joinedCommand)
    }.start()

    /**
     * The WebSocket address to connect to.
     */
    private val debugUrl: String = run {
        val prompt = nodeProcess.errorStream.bufferedReader().readLine()
        logger.fine(prompt)
        if (prompt.startsWith(NODE_WS_DEBUG_URL_PREFIX)) {
            val startIndexInLine = NODE_WS_DEBUG_URL_PREFIX.length - "ws://".length
            prompt.substring(startIndexInLine).trim()
        } else {
            error(prompt)
        }
    }

    private val webSocketClient = HttpClient(CIO) {
        install(WebSockets)
        engine {
            requestTimeout = 0
        }
    }

    private var webSocketSession = runBlocking { openWebSocketSession() }

    private suspend fun openWebSocketSession() = webSocketClient.webSocketSession(debugUrl).also {
        logger.fine { "Opened a websocket session: $it" }
    }

    data class MessageContinuation(
        val encodingInfo: CDPMethodCallEncodingInfo,
        val continuation: Continuation<CDPMethodInvocationResult>,
    )

    val messageContinuations = mutableMapOf<Int, MessageContinuation>()

    data class WaitingOnPredicate(
        val predicate: () -> Boolean,
        val continuation: Continuation<Unit>,
    )

    /**
     * See [waitForConditionToBecomeTrue].
     */
    var waitingOnPredicate: WaitingOnPredicate? = null

    private var nextMessageId = 0

    private val loggingJsonPrettyPrinter by lazy { Json { prettyPrint = true } }

    private fun prettyPrintJson(json: String): String {
        val jsonElement = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return json
        }
        return loggingJsonPrettyPrinter.encodeToString(jsonElement)
    }

    /**
     * Starts a loop that waits for incoming Chrome DevTools Protocol messages and invokes [receiveMessage] when one is received.
     * The loop stops as soon as at least one message is received *and* [receiveMessage] returns `true`.
     */
    suspend fun listenForMessages(receiveMessage: (String) -> Boolean) {
        while (true) {
            val message = when (val frame = webSocketSession.incoming.receive()) {
                is Frame.Text -> frame.readText()
                else -> error("Unexpected frame kind: $frame")
            }
            logger.finer {
                "Received message:\n${prettyPrintJson(message)}"
            }
            if (receiveMessage(message)) break
        }
    }

    override var associatedState: Any? = null

    override val debugger = Debugger(this)

    override val runtime = Runtime(this)

    override suspend fun waitForConditionToBecomeTrue(predicate: () -> Boolean) {
        if (predicate()) return
        suspendCoroutine { continuation ->
            require(waitingOnPredicate == null) { "already waiting!" }
            waitingOnPredicate = WaitingOnPredicate(predicate, continuation)
        }
    }

    private suspend fun sendPlainTextMessage(message: String) {
        logger.finer {
            "Sent message:\n${prettyPrintJson(message)}"
        }
        webSocketSession.send(message)
    }

    @Deprecated("Only for debugging purposes", level = DeprecationLevel.WARNING)
    override suspend fun sendPlainTextMessage(methodName: String, paramsJson: String): String {
        val messageId = nextMessageId++
        sendPlainTextMessage("""{"id":$messageId,"method":$methodName,"params":$paramsJson}""")
        return suspendCoroutine { continuation ->
            messageContinuations[messageId] = MessageContinuation(CDPMethodCallEncodingInfoPlainText, continuation)
        }.cast<CDPMethodInvocationResultPlainText>().string
    }

    override suspend fun genericEvaluateRequest(
        encodeMethodCallWithMessageId: (Int) -> Pair<String, CDPMethodCallEncodingInfo>
    ): CDPMethodInvocationResult {
        val messageId = nextMessageId++
        val (encodedMessage, encodingInfo) = encodeMethodCallWithMessageId(messageId)
        sendPlainTextMessage(encodedMessage)
        return suspendCoroutine { continuation ->
            messageContinuations[messageId] = MessageContinuation(encodingInfo, continuation)
        }
    }

    fun writeln(content: String) {
        logger.fine { "Writeln to node's stdin:\n$content" }
        val writer = nodeProcess.outputStream.writer()
        writer.write(content + "\n")
        writer.flush()
    }

    /**
     * Releases all the resources and destroys the Node.js process.
     */
    fun release() = runBlocking {
        logger.fine { "Releasing $this" }
        webSocketSession.close()
        webSocketClient.close()
        nodeProcess.destroy()
    }
}

private fun ProcessBuilder.joinedCommand(): String =
    command().joinToString(" ") { "\"${it.replace("\"", "\\\"")}\"" }
