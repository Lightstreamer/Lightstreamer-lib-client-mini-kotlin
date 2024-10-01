package com.lightstreamer.client.socket


import com.lightstreamer.client.socket.internal.FastCharSequence
import com.lightstreamer.client.socket.internal.TlcpParser
import com.lightstreamer.client.socket.internal.appendTlcpEncoded
import com.lightstreamer.client.socket.internal.appendTlcpEncodedParameters
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerClientRequestName
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.future.await
import mu.KLogging
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletionStage
import kotlin.time.Duration.Companion.seconds

public class LightstreamerTlcpSocket
private constructor(
    private val webSocket: WebSocket,
    private val webSocketListener: WebSocketListener
) : LightstreamerSocket, ReceiveChannel<LightstreamerServerMessage> by webSocketListener.serverMessageChannel {

    private val sendChannel =
        Channel<LightstreamerClientMessage>(256)

    private val sendJob: Job = GlobalScope.launch {
        val sendBuffer = FastCharSequence(128)
        var pendingMessage: LightstreamerClientMessage? = null

        try {
            while (true) {
                val firstMessage = pendingMessage ?: sendChannel.receive()
                sendBuffer.appendTlcpEncoded(firstMessage)
                pendingMessage = null

                // collapse multiple requests
                if (firstMessage.name == LightstreamerClientRequestName.CONTROL || firstMessage.name == LightstreamerClientRequestName.MESSAGE) {
                    while (sendBuffer.length < SEND_THRESHOLD) {
                        pendingMessage = sendChannel.tryReceive().getOrNull() ?: break
                        if (pendingMessage.name != firstMessage.name) break
                        sendBuffer.appendTlcpEncodedParameters(pendingMessage)
                        pendingMessage = null
                    }
                }

                ensureActive()
                logger.trace { "Send to $webSocket: $sendBuffer" }
                webSocket.sendText(sendBuffer, true)?.await()
                sendBuffer.clear()
            }
        } catch (e: Exception) {
            if (e is CancellationException) logger.trace(e) { "Error sending messages to $webSocket" }
            else logger.info { "Error sending messages to $webSocket: $e" }
            throw e
        } finally {
            sendChannel.close()
        }
    }

    override fun disconnect() {
        logger.trace { "Disconnect $webSocket" }
        sendJob.cancel()
        sendChannel.close()
        webSocketListener.close(webSocket)
    }

    override suspend fun join() {
        webSocketListener.closeCauseDeferred.join()
    }

    override suspend fun send(message: LightstreamerClientMessage) {
        logger.debug { "Enqueue message for $webSocket: $message" }
        try {
            sendChannel.send(message)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "Error sending message to $webSocket", e)
        }
    }

    override fun toString(): String = webSocket.toString()

    private class WebSocketListener : WebSocket.Listener {

        val serverMessageChannel = Channel<LightstreamerServerMessage>(1024)

        private val tlcpParser = TlcpParser()

        val closeCauseDeferred = CompletableDeferred<Throwable?>()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<Unit>? {
            try {
                logger.debug { "$webSocket onText $data" }
                for (message in tlcpParser.parse(data)) {
                    serverMessageChannel.trySendBlocking(message)
                        .onFailure { throw it ?: IllegalStateException("Channel closed") }
                }
                webSocket.request(1)
            } catch (t: Throwable) {
                close(webSocket, t)
            }
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
            logger.debug { "$webSocket onClose $statusCode $reason" }
            val exception =
                if (statusCode == WebSocket.NORMAL_CLOSURE) null
                else IOException("WebSocket closed: $statusCode $reason")
            close(webSocket, exception)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            logger.debug { "$webSocket onError $error" }
            close(webSocket, error)
        }

        fun close(webSocket: WebSocket, cause: Throwable? = null) {
            if (cause !is CancellationException) logger.info { "Close $webSocket: $cause" }
            closeCauseDeferred.complete(cause)
            if (cause == null || cause is ClosedReceiveChannelException) serverMessageChannel.close()
            else serverMessageChannel.close(cause)
            webSocket.abort()
        }
    }

    public companion object : KLogging() {

        @Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
        public val clientCidCode: String get() = "mgQkwtwdysogQz2BJ4Ji kOj2Bg"

        private const val SEND_THRESHOLD = 8 * 1024

        @Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
        public val tlcpVersion: String get() = "2.5.0"

        /**
         * Opens a WebSocket connection.
         */
        public suspend fun connect(address: LightstreamerServerAddress, httpClient: HttpClient): LightstreamerSocket {
            logger.debug { "connect($address)" }

            val webSocketListener = WebSocketListener()

            val protocol = if (address.secureConnection) "wss" else "ws"
            val webSocket: WebSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .subprotocols("TLCP-$tlcpVersion.lightstreamer.com")
                .buildAsync(
                    URI(protocol, null, address.host, address.port.toInt(), "/lightstreamer", null, null),
                    webSocketListener
                )
                .await()

            return LightstreamerTlcpSocket(
                webSocket = webSocket,
                webSocketListener = webSocketListener
            )
        }

        /**
         * Send messages to a LightStreamer server using the "Control Combo Request"
         */
        public suspend fun sendMessages(
            address: LightstreamerServerAddress,
            adapterSetName: String = "DEFAULT",
            userCredential: UsernamePassword? = null,
            httpClient: HttpClient,
            messages: Iterable<String>
        ) {
            val requestBody = buildString {
                appendTlcpEncodedParameters(
                    LightstreamerClientMessage.CreateSession(
                        adapterSetName,
                        userCredential,
                        polling = 0.seconds
                    )
                )
                for (message in messages) {
                    append("LS_message=")
                    appendTlcpEncoded(message)
                    append("&LS_outcome=false")
                    append("\r\n")
                }
            }

            val protocol = if (address.secureConnection) "https" else "http"
            val request = HttpRequest
                .newBuilder(
                    URI(
                        protocol,
                        null,
                        address.host,
                        address.port.toInt(),
                        "/lightstreamer/create_session.txt",
                        "LS_protocol=TLCP-$tlcpVersion",
                        null
                    )
                )
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

            if (response.statusCode() != 200) throw IOException("Unable to send messages, HTTP status code ${response.statusCode()}")
            when (val serverResponse = TlcpParser().parse(response.body()).firstOrNull()) {
                is LightstreamerServerMessage.ConnectionOk -> Unit
                is LightstreamerServerMessage.ConnectionError ->
                    throw LightstreamerServerException.ConnectionError(serverResponse.code, serverResponse.message)

                else -> throw IOException("Unable to parse response: ${response.body()}")
            }
        }
    }
}
