package com.lightstreamer.kotlin.client.internal

import com.lightstreamer.kotlin.client.*
import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KLogging
import kotlin.coroutines.CoroutineContext

internal class LightstreamerSubscriptionImpl(
    internal val id: Int,
    override val dataAdapterName: String,
    override val mode: SubscriptionMode,
    override val itemGroup: String,
    override val itemFields: LightstreamerSubscription.FieldList,
    private val channel: Channel<LightstreamerSubscriptionMessage> =
        Channel(if (mode.supportEndOfSnapshot) 8 else Channel.RENDEZVOUS),
    parentJob: Job,
) : LightstreamerSubscription, ReceiveChannel<LightstreamerSubscriptionMessage> by channel {

    private val completableExitStatus = CompletableDeferred<Unit>(parentJob)

    override val exitStatus: Deferred<Unit> get() = completableExitStatus

    override val coroutineContext: CoroutineContext = completableExitStatus + CoroutineName(toString())

    private var updateProcessor: SubscriptionUpdateProcessor? = null

    init {
        // close channel when subscription completes
        coroutineContext.job.invokeOnCompletion { channel.cancel() }
    }

    suspend fun consumeServerMessage(subscriptionServerMessage: LightstreamerServerMessage.SubscriptionServerMessage) {
        try {
            // handle regular update
            val subscriptionMessage = when (subscriptionServerMessage) {
                is LightstreamerServerMessage.Update ->
                    updateProcessor?.invoke(subscriptionServerMessage)
                        ?: error("Unable to handle update $subscriptionServerMessage")

                is LightstreamerServerMessage.ClearSnapshot ->
                    LightstreamerSubscriptionMessage.ClearSnapshot(subscriptionServerMessage.itemId)

                is LightstreamerServerMessage.EndOfSnapshot ->
                    LightstreamerSubscriptionMessage.EndOfSnapshot(subscriptionServerMessage.itemId)

                is LightstreamerServerMessage.Overflow ->
                    LightstreamerSubscriptionMessage.Overflow(
                        item = subscriptionServerMessage.item,
                        overflowSize = subscriptionServerMessage.overflowSize
                    )

                is LightstreamerServerMessage.Configuration ->
                    LightstreamerSubscriptionMessage.Configuration(
                        maxFrequency = subscriptionServerMessage.maxFrequency,
                        filtered = subscriptionServerMessage.filtered
                    )

                // handle subscription OK
                is LightstreamerServerMessage.SubscriptionOk -> {
                    check(updateProcessor == null) { "Subscription OK already received" }
                    updateProcessor = SubscriptionUpdateProcessor(
                        itemCount = subscriptionServerMessage.itemCount,
                        fieldCount = subscriptionServerMessage.fieldCount,
                        itemFields = itemFields
                    )
                    LightstreamerSubscriptionMessage.SubscriptionOk(
                        itemCount = subscriptionServerMessage.itemCount,
                        fieldCount = subscriptionServerMessage.fieldCount
                    )
                }

                is LightstreamerServerMessage.SubscriptionCommandOk -> {
                    check(updateProcessor == null) { "Subscription OK already received" }
                    updateProcessor = SubscriptionUpdateProcessor(
                        itemCount = subscriptionServerMessage.itemCount,
                        fieldCount = subscriptionServerMessage.fieldCount,
                        itemFields = itemFields
                    )
                    LightstreamerSubscriptionMessage.SubscriptionOk(
                        itemCount = subscriptionServerMessage.itemCount,
                        fieldCount = subscriptionServerMessage.fieldCount,
                        keyFieldPos = subscriptionServerMessage.keyFieldPos,
                        commandFieldPos = subscriptionServerMessage.commandFieldPos
                    )
                }

                else -> {
                    logger.warn { "${this@LightstreamerSubscriptionImpl}: Invalid message $subscriptionServerMessage" }
                    throw IllegalArgumentException("Invalid message $subscriptionServerMessage")
                }
            }
            logger.debug { "${this@LightstreamerSubscriptionImpl} send $subscriptionMessage" }
            channel.send(subscriptionMessage)
        } catch (e: CancellationException) {
            cancel(e)
        } catch (e: Exception) {
            logger.warn(e) { "${this@LightstreamerSubscriptionImpl}: Unexpected error" }
            cancel(CancellationException("$this cancelled").apply { initCause(e) })
        }
    }

    override fun cancel(cause: CancellationException?) {
        if (cause?.cause != null) logger.debug(cause) { "${this@LightstreamerSubscriptionImpl} cancel" }
        else logger.debug { "${this@LightstreamerSubscriptionImpl} cancel, cause: $cause" }
        channel.cancel(cause)
        if (cause == null) completableExitStatus.complete(Unit)
        else completableExitStatus.cancel(cause)
    }

    override fun toString() =
        "${javaClass.simpleName}{subscriptionId=$id, dataAdapterName=$dataAdapterName, mode=$mode, itemGroup=$itemGroup, itemFields=$itemFields}"

    private companion object : KLogging()
}
