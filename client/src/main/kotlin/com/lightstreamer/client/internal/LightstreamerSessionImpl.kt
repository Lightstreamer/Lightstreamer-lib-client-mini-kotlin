package com.lightstreamer.client.internal

import com.lightstreamer.client.LightstreamerCancellationException
import com.lightstreamer.client.LightstreamerClient
import com.lightstreamer.client.LightstreamerSession
import com.lightstreamer.client.LightstreamerSubscription
import com.lightstreamer.client.socket.LightstreamerServerAddress
import com.lightstreamer.client.socket.LightstreamerServerException
import com.lightstreamer.client.socket.LightstreamerSocket
import com.lightstreamer.client.socket.SubscriptionMode
import com.lightstreamer.client.socket.diff.SupportedDiff
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerMessageResponse
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.whileSelect
import mu.KLogging
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class LightstreamerSessionImpl(
    private val lightstreamerClient: LightstreamerClient,
    private val socketBuilder: suspend (serverAddress: LightstreamerServerAddress) -> LightstreamerSocket
) : LightstreamerSession {

    private val pendingRequestMap = ConcurrentHashMap<String, PendingRequest>()
    private val pendingMessageMap = ConcurrentHashMap<MessageKey, CompletableDeferred<LightstreamerMessageResponse>>()
    private val subscriptionsMap = ConcurrentHashMap<Int, LightstreamerSubscriptionImpl>()
    private val pendingClientMessageChannel = Channel<LightstreamerClientMessage>(Channel.UNLIMITED)

    private val messageSequenceProgressiveHolder = MessageSequenceProgressiveHolder()

    private val coroutineScope = CoroutineScope(Job(lightstreamerClient.job))

    private val requestsParentJob = SupervisorJob(coroutineScope.coroutineContext.job)
    private val subscriptionsParentJob = SupervisorJob(coroutineScope.coroutineContext.job)

    @Suppress("unused")
    @Volatile
    private var subscriptionIdGenerator = 0

    override val exitStatus: Deferred<LightstreamerServerException?>

    /**
     * Data notification received count
     */
    private var dataNotificationCount = 0

    // remote server/controlLink address
    private var serverAddress = lightstreamerClient.serverAddress

    private val sessionIdCompletable = CompletableDeferred<String>(parent = coroutineScope.coroutineContext.job)
    override val sessionId: Deferred<String> = sessionIdCompletable

    init {
        val completableExitStatus = CompletableDeferred<LightstreamerServerException?>()
        exitStatus = completableExitStatus

        // this coroutine ensures regular clean-up
        // so this should not terminate on job end
        coroutineScope.launch(
            CoroutineName("LightstreamerSessionImpl(adapterSetName=${lightstreamerClient.adapterSetName},serverAddress=$serverAddress)"),
            start = CoroutineStart.ATOMIC
        ) {
            val exitResult: Result<LightstreamerServerException?> = runCatching {
                while (coroutineScope.isActive) {
                    val lightstreamerServerException = connect()
                    if (lightstreamerServerException != null)
                        return@runCatching lightstreamerServerException
                }
                return@runCatching null
            }

            exitResult
                .onSuccess(completableExitStatus::complete)
                .onFailure(completableExitStatus::completeExceptionally)

            // cancel pending tasks
            val cause =
                exitResult.getOrNull()?.let(::LightstreamerCancellationException)
                    ?: exitResult.exceptionOrNull()?.let { e ->
                        CancellationException("Fatal error in ${this@LightstreamerSessionImpl}: $e", e)
                    }
                    ?: CancellationException("${this@LightstreamerSessionImpl} closed")

            pendingClientMessageChannel.close(cause)
            // clean up
            pendingRequestMap.clear()
            pendingMessageMap.clear()
            subscriptionsMap.clear()
            coroutineScope.cancel(cause)
        }
    }

    private suspend fun connect(): LightstreamerServerException? {
        logger.info { "${this@LightstreamerSessionImpl} Connecting..." }
        var socket = socketBuilder(serverAddress)
        return try {
            coroutineScope {
                val connectionOk: LightstreamerServerMessage.ConnectionOk =
                    if (!sessionIdCompletable.isCompleted) {
                        // create a session
                        val requestPolling = lightstreamerClient.forceControlLink
                        socket.send(
                            // create a session
                            LightstreamerClientMessage.CreateSession(
                                adapterSetName = lightstreamerClient.adapterSetName,
                                userCredential = lightstreamerClient.userCredential,
                                keepAlive = lightstreamerClient.keepAlive,
                                inactivity = lightstreamerClient.inactivity,
                                polling = Duration.ZERO.takeIf { requestPolling },
                                supportedDiffs = setOf(SupportedDiff.TLCPDiff),
                                reduceHead = true,
                                sendSync = false,
                                ttl = lightstreamerClient.createSessionTTL
                            )
                        )
                        var connectionOk = socket.receiveConnectionOk()
                        sessionIdCompletable.complete(connectionOk.sessionId)

                        // parse controlLink
                        val controlLink = connectionOk.controlLink?.let { string ->
                            val parts = string.split(':', limit = 2)
                            val host = parts[0]
                            val port: UShort = parts.getOrNull(1)?.toUShort()
                                ?: if (lightstreamerClient.serverAddress.secureConnection) 443.toUShort() else 80.toUShort()
                            LightstreamerServerAddress(
                                host = host,
                                port = port,
                                secureConnection = lightstreamerClient.serverAddress.secureConnection
                            )
                        }

                        if (controlLink != null) {
                            serverAddress = controlLink
                        }

                        if (requestPolling) {
                            // await loop request
                            do {
                                val message = socket.receive()
                            } while (message !is LightstreamerServerMessage.Loop)

                            if (lightstreamerClient.forceControlLink) {
                                // disconnect and connect to control link
                                socket.disconnect()
                                socket = socketBuilder(serverAddress)
                                socket.send(
                                    // rebind the session
                                    // this BIND must be coherent with LOOP response
                                    LightstreamerClientMessage.BindSession(
                                        session = sessionId.getCompleted(),
                                        keepAlive = lightstreamerClient.keepAlive,
                                        inactivity = lightstreamerClient.inactivity,
                                        sendSync = false
                                    )
                                )
                                connectionOk = try {
                                    socket.receiveConnectionOk()
                                } catch (lse: LightstreamerServerException.ConnectionError) {
                                    throw LightstreamerServerException.ConnectionError(
                                        lse.code, "Unable to bind on $controlLink (${lse.description})"
                                    )
                                }
                            }
                        }

                        connectionOk
                    } else coroutineScope {
                        // recover the session
                        socket.send(
                            // this BIND must be coherent with LOOP response
                            LightstreamerClientMessage.BindSession(
                                session = sessionId.getCompleted(),
                                recoveryFrom = dataNotificationCount,
                                keepAlive = lightstreamerClient.keepAlive,
                                inactivity = lightstreamerClient.inactivity,
                                sendSync = false
                            )
                        )

                        // resend pending requests
                        launch {
                            for (pendingRequest in pendingRequestMap.values) {
                                socket.send(pendingRequest.request)
                            }
                        }

                        val connectionOk =
                            try {
                                socket.receiveConnectionOk()
                            } catch (lse: LightstreamerServerException.ConnectionError) {
                                throw LightstreamerServerException.ConnectionError(
                                    lse.code,
                                    "Session ${sessionId.getCompleted()} recovery failed (${lse.description})",
                                    lse
                                )
                            }
                        val keepAlive = connectionOk.keepAlive
                        require(sessionId.getCompleted() == connectionOk.sessionId) { "Connection recovered on a wrong session: ${sessionId.getCompleted()} != ${connectionOk.sessionId}" }

                        // sync data notification count
                        // await server progressive message
                        var messageToSkip: Int = -1
                        while (messageToSkip < 0) {
                            val serverMessage =
                                if (keepAlive == null) socket.receive()
                                else withTimeout(keepAlive.inWholeMilliseconds.coerceAtLeast(1)) { socket.receive() }
                            if (serverMessage is LightstreamerServerMessage.Progressive) {
                                messageToSkip = dataNotificationCount - serverMessage.progressive
                                require(messageToSkip >= 0) { "Progressive $serverMessage is greater than $dataNotificationCount" }
                            } else {
                                if (serverMessage.isDataNotification()) messageToSkip = 0
                                // manage server message
                                onServerMessage(serverMessage)
                            }
                        }

                        // read messages and skip data notification already received
                        while (messageToSkip > 0) {
                            logger.debug { "${this@LightstreamerSessionImpl} skip $messageToSkip data notification" }
                            val serverMessage =
                                if (keepAlive == null) socket.receive()
                                else withTimeout(keepAlive.inWholeMilliseconds.coerceAtLeast(1)) { socket.receive() }
                            if (serverMessage.isDataNotification()) messageToSkip--
                            else onServerMessage(serverMessage)
                        }

                        connectionOk
                    }


                val sendClientMessageDeferred: Deferred<LightstreamerServerException?> = async(
                    Dispatchers.Unconfined + CoroutineName("${LightstreamerSessionImpl::class.simpleName}.sendClientMessage($serverAddress)"),
                    start = CoroutineStart.LAZY
                ) {
                    try {
                        val heartbeatTimeout = lightstreamerClient.inactivity
                            ?.inWholeMilliseconds?.coerceAtLeast(1)
                        if (heartbeatTimeout == null) {
                            for (message in pendingClientMessageChannel) socket.send(message)
                        } else {
                            while (isActive) {
                                select<Unit> {
                                    pendingClientMessageChannel.onReceive { clientMessage ->
                                        socket.send(clientMessage)
                                    }
                                    onTimeout(heartbeatTimeout) {
                                        // server waits <session_timeout_millis> extra time
                                        socket.send(LightstreamerClientMessage.Heartbeat)
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                    } catch (e: ClosedSendChannelException) {
                    } catch (e: LightstreamerServerException) {
                        return@async e
                    } catch (e: IOException) {
                        logger.warn { "Error sending message to $socket: $e" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Error sending message to $socket" }
                    }
                    return@async null
                }

                val keepAlive = connectionOk.keepAlive
                // consume server messages
                val receiveClientMessageDeferred: Deferred<LightstreamerServerException?> = async(
                    CoroutineName("${LightstreamerSessionImpl::class.simpleName}.consumeServerMessage($serverAddress)"),
                    start = CoroutineStart.LAZY
                ) {
                    try {
                        val keepAliveTimeout = keepAlive?.plus(lightstreamerClient.keepAliveExtra)
                            ?.inWholeMilliseconds?.coerceAtLeast(1)
                        if (keepAliveTimeout == null) {
                            for (message in socket) onServerMessage(message)
                        } else {
                            whileSelect {
                                socket.onReceive { serverMessage ->
                                    onServerMessage(serverMessage)
                                    true
                                }
                                onTimeout(keepAliveTimeout) {
                                    // terminate consumer and close socket
                                    logger.warn { "Keep alive ($keepAlive) expired on session ${this@LightstreamerSessionImpl}" }
                                    false
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                    } catch (e: ClosedReceiveChannelException) {
                    } catch (e: LightstreamerServerException) {
                        return@async e
                    } catch (e: IOException) {
                        logger.warn { "${this@LightstreamerSessionImpl} Error while waiting message from $socket: $e" }
                    } catch (e: Exception) {
                        logger.warn(e) { "${this@LightstreamerSessionImpl} Error while waiting message from $socket" }
                    }
                    return@async null
                }

                logger.info { "${this@LightstreamerSessionImpl} Connected" }
                return@coroutineScope select {
                    sendClientMessageDeferred.onAwait { e ->
                        receiveClientMessageDeferred.cancel()
                        e
                    }
                    receiveClientMessageDeferred.onAwait { e ->
                        sendClientMessageDeferred.cancel()
                        e
                    }
                }
            }
        } catch (e: CancellationException) {
            logger.trace { "${this@LightstreamerSessionImpl} cancelled: $e" }
            throw e
        } catch (e: IOException) {
            logger.warn { "${this@LightstreamerSessionImpl} I/O error on create connection: $e" }
            throw e
        } catch (e: LightstreamerServerException) {
            logger.warn { "${this@LightstreamerSessionImpl} Server error on create connection: $e" }
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "${this@LightstreamerSessionImpl} Error on create connection" }
            throw e
        } finally {
            // destroy session on termination
            if (!coroutineScope.isActive) try {
                withTimeout(lightstreamerClient.keepAliveExtra.coerceIn(100.milliseconds, 1.seconds)) {
                    socket.send(LightstreamerClientMessage.Destroy(closeSocket = true))
                }
            } catch (e: Exception) {
                logger.trace { "${this@LightstreamerSessionImpl} Unable to destroy session: $e" }
            }
            logger.trace { "${this@LightstreamerSessionImpl} Disconnect $socket" }
            socket.disconnect()
        }
    }

    override fun close() {
        val sid = sessionId.takeIf { it.isCompleted }?.getCompleted()
        coroutineScope.cancel("Lightstreamer session $sid has been closed")
    }

    override suspend fun sendMessage(message: String, sequenceName: String?): LightstreamerMessageResponse {
        require(sequenceName == null || sequenceName.isNotEmpty()) { "Sequence name cannot be empty" }
        val progressive = messageSequenceProgressiveHolder[sequenceName ?: ""]
        val request = LightstreamerClientMessage.Message(
            message = message,
            sequence = sequenceName,
            msgProg = progressive,
            ack = false
        )
        logger.debug { "$this:sendMessage $request" }
        val messageKey = MessageKey(sequenceName, progressive)
        val messageDeferred = CompletableDeferred<LightstreamerMessageResponse>()
        pendingMessageMap[messageKey] = messageDeferred
        // sendRequest throws only (ack==false)
        val sendRequestDeferred = sendRequestAsync(request)
        try {
            return select {
                messageDeferred.onAwait { it }
                sendRequestDeferred.onAwait { r ->
                    r.onFailure { throw it }
                    messageDeferred.await()
                }
            }
        } finally {
            sendRequestDeferred.cancel()
            messageDeferred.cancel()
            pendingMessageMap.remove(messageKey)
        }
    }

    override fun subscribe(
        mode: SubscriptionMode,
        dataAdapterName: String,
        itemGroup: String,
        itemFields: LightstreamerSubscription.FieldList,
        requestSnapshot: Boolean
    ): LightstreamerSubscription {
        val subscription = LightstreamerSubscriptionImpl(
            id = subscriptionIdGeneratorUpdater.incrementAndGet(this),
            mode = mode,
            dataAdapterName = dataAdapterName,
            itemGroup = itemGroup,
            itemFields = itemFields,
            parentJob = subscriptionsParentJob
        )
        logger.debug { "$this:subscribe $subscription" }
        subscriptionsMap[subscription.id] = subscription

        // send subscribe
        val sendRequestDeferred = sendRequestAsync(
            LightstreamerClientMessage.Subscribe(
                subscriptionId = subscription.id,
                dataAdapterName = dataAdapterName,
                mode = mode,
                itemGroup = itemGroup,
                itemSchema = subscription.itemFields,
                requestSnapshot = requestSnapshot,
                ack = false
            )
        )

        sendRequestDeferred.invokeOnCompletion { completionException ->
            val requestError = completionException ?: sendRequestDeferred.getCompleted().exceptionOrNull()
            if (requestError == null) {
                // send an unsubscription on subscription's completion
                subscription.exitStatus.invokeOnCompletion {
                    subscriptionsMap.remove(subscription.id)
                    if (coroutineScope.isActive) {
                        @Suppress("DeferredResultUnused")
                        sendRequestAsync(LightstreamerClientMessage.Unsubscribe(subscription.id))
                    }
                }
            } else {
                // cancel subscription in case of error
                subscriptionsMap.remove(subscription.id)
                if (requestError is LightstreamerServerException) {
                    subscription.cancel(LightstreamerCancellationException(requestError))
                } else {
                    subscription.cancel(CancellationException(requestError.message, completionException))
                }
            }
        }

        return subscription
    }

    private fun sendRequestAsync(clientMessage: LightstreamerClientMessage): Deferred<Result<LightstreamerServerMessage.RequestOk>> {
        logger.debug { "$this:sendRequest $clientMessage" }
        val pendingRequest = PendingRequest(clientMessage, requestsParentJob)
        pendingRequestMap[clientMessage.id] = pendingRequest
        pendingRequest.invokeOnCompletion { pendingRequestMap.remove(clientMessage.id) }
        pendingClientMessageChannel.trySend(clientMessage) // unlimited channel
        return pendingRequest
    }

    private suspend fun onServerMessage(message: LightstreamerServerMessage) {
        logger.debug { "$this:onServerMessage $message" }
        if (message.isDataNotification()) dataNotificationCount++
        try {
            @Suppress("IMPLICIT_CAST_TO_ANY")
            when (message) {
                //subscription
                is LightstreamerServerMessage.SubscriptionOkServerMessage -> {
                    // generate and process unrequested ReqOk (sub ack=false)
                    val requestId =
                        LightstreamerClientMessage.Subscribe.requestIdForSubscription(message.subscriptionId)
                    pendingRequestMap.remove(requestId)
                        ?.complete(Result.success(LightstreamerServerMessage.RequestOk(requestId)))
                    subscriptionsMap[message.subscriptionId]?.consumeServerMessage(message)
                }

                is LightstreamerServerMessage.UnsubscriptionOk ->
                    subscriptionsMap.remove(message.subscriptionId)?.cancel()

                is LightstreamerServerMessage.SubscriptionServerMessage ->
                    subscriptionsMap[message.subscriptionId]?.consumeServerMessage(message)

                // request
                is LightstreamerServerMessage.RequestOk ->
                    pendingRequestMap.remove(message.requestId)?.complete(Result.success(message))

                is LightstreamerServerMessage.RequestError ->
                    pendingRequestMap.remove(message.requestId)?.complete(
                        Result.failure(LightstreamerServerException.RequestError(message.code, message.message))
                    )

                // message
                is LightstreamerServerMessage.MessageDone ->
                    pendingMessageMap.remove(MessageKey(message.sequence, message.prog))
                        ?.complete(LightstreamerMessageResponse.Done(message.response))

                is LightstreamerServerMessage.MessageFail ->
                    pendingMessageMap.remove(MessageKey(message.sequence, message.prog))
                        ?.complete(LightstreamerMessageResponse.Fail(message.errorCode, message.errorMessage))

                // others
                is LightstreamerServerMessage.ConnectionOk -> Unit // after Loop and BindSession
                is LightstreamerServerMessage.ClientIp -> Unit
                is LightstreamerServerMessage.Constraint -> Unit
                is LightstreamerServerMessage.Loop -> coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // a bind will be sent on a loop
                    pendingClientMessageChannel.send(
                        LightstreamerClientMessage.BindSession(
                            session = sessionId.getCompleted(),
                            keepAlive = lightstreamerClient.keepAlive,
                            inactivity = lightstreamerClient.inactivity,
                            sendSync = false
                        )
                    )
                }

                is LightstreamerServerMessage.NoOp -> Unit
                is LightstreamerServerMessage.Probe -> Unit
                is LightstreamerServerMessage.ServerName -> Unit
                is LightstreamerServerMessage.Synchronize -> Unit

                // termination
                is LightstreamerServerMessage.ConnectionError ->
                    throw LightstreamerServerException.ConnectionError(message.code, message.message)

                is LightstreamerServerMessage.End ->
                    throw LightstreamerServerException.End(message.code, message.message)

                is LightstreamerServerMessage.Error ->
                    throw LightstreamerServerException.Error(message.code, message.message)

                is LightstreamerServerMessage.Progressive ->
                    throw IOException("Unexpected message $message")
            }.let { }
        } catch (e: Exception) {
            logger.debug(e) { "$this Error on server message: $message" }
            throw e
        }
        logger.trace { "$this:onServerMessage $message done" }
    }

    override fun toString(): String =
        sessionId.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted() +
                "@${lightstreamerClient.serverAddress}" +
                '(' +
                "subscriptions=${subscriptionsMap.size}, " +
                "pendingMessages=${pendingMessageMap.size}, " +
                "pendingRequests=${pendingRequestMap.size}" +
                ')'

    private data class MessageKey(val sequenceName: String?, val progressive: Int)

    private class PendingRequest(val request: LightstreamerClientMessage, parentJob: Job) :
        CompletableDeferred<Result<LightstreamerServerMessage.RequestOk>> by CompletableDeferred(parent = parentJob) {
        override fun toString(): String =
            "$request(isActive=$isActive,isCompleted=$isCompleted,isCancelled=$isCancelled)"
    }

    private companion object : KLogging() {

        private val subscriptionIdGeneratorUpdater: AtomicIntegerFieldUpdater<LightstreamerSessionImpl> =
            AtomicIntegerFieldUpdater.newUpdater(LightstreamerSessionImpl::class.java, "subscriptionIdGenerator")

        private suspend fun ReceiveChannel<LightstreamerServerMessage>.receiveConnectionOk(): LightstreamerServerMessage.ConnectionOk {
            val message = receive()
            if (message is LightstreamerServerMessage.ConnectionError) {
                throw LightstreamerServerException.ConnectionError(message.code, message.message)
            }
            if (message is LightstreamerServerMessage.End) {
                throw LightstreamerServerException.ConnectionError(message.code, message.message)
            }
            if (message !is LightstreamerServerMessage.ConnectionOk) {
                throw IOException("Connection bad response: $message")
            }
            return message
        }
    }
}
