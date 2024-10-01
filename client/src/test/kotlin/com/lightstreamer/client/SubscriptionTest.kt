package com.lightstreamer.client

import com.lightstreamer.client.internal.filterUpdate
import com.lightstreamer.client.internal.listOfValues
import com.lightstreamer.client.internal.test
import com.lightstreamer.client.socket.SubscriptionMode
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import com.lightstreamer.client.socket.message.LightstreamerSubscriptionMessage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.Test

class SubscriptionTest {

    @Test
    fun `subscription OK`() = test {
        val updateDeferred: Deferred<LightstreamerSubscriptionMessage.Update> = async {
            session.subscribe(
                SubscriptionMode.RAW,
                "dataAdapter",
                "group",
                LightstreamerSubscription.FieldList("f"),
                requestSnapshot = true
            )
                .filterUpdate().consumeAsFlow()
                .first()
        }

        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        subscribe.mode `should be equal to` SubscriptionMode.RAW
        subscribe.dataAdapterName `should be equal to` "dataAdapter"
        subscribe.itemGroup `should be equal to` "group"
        subscribe.itemSchema `should be equal to` listOf("f")
        subscribe.requestSnapshot `should be equal to` true
        subscribe.ack `should be equal to` false
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Update(subscribe.subscriptionId, 1, listOfValues("v")))

        val update = updateDeferred.await()
        update["f"] `should be equal to` "v"
    }

    @Test
    fun `subscription cancel before subscribe`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )
        subscription.cancel()
        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Configuration(subscribe.subscriptionId, null, false))

        val unsubscribe = clientChannel.receive() as LightstreamerClientMessage.Unsubscribe
        unsubscribe.subscriptionId `should be equal to` subscribe.subscriptionId
        subscription.exitStatus.join()
        subscription.isClosedForReceive `should be equal to` true
    }

    @Test
    fun `subscription cancel after subscribe`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )
        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Configuration(subscribe.subscriptionId, null, false))
        whileSelect { subscription.onReceive { it is LightstreamerSubscriptionMessage.SubscriptionOk } }

        subscription.cancel()
        val unsubscribe = clientChannel.receive() as LightstreamerClientMessage.Unsubscribe
        unsubscribe.subscriptionId `should be equal to` subscribe.subscriptionId
        subscription.exitStatus.join()
        subscription.isClosedForReceive `should be equal to` true
    }

    @Test
    fun `command subscription cancel after subscribe`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.COMMAND,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("command", "key"),
            requestSnapshot = true
        )
        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.SubscriptionCommandOk(subscribe.subscriptionId, 1u, 1u, 1u, 0u))
        serverChannel.send(LightstreamerServerMessage.Configuration(subscribe.subscriptionId, null, false))
        whileSelect { subscription.onReceive { it is LightstreamerSubscriptionMessage.SubscriptionOk } }

        subscription.cancel()
        val unsubscribe = clientChannel.receive() as LightstreamerClientMessage.Unsubscribe
        unsubscribe.subscriptionId `should be equal to` subscribe.subscriptionId
        subscription.exitStatus.join()
        subscription.isClosedForReceive `should be equal to` true
    }

    @Test
    fun `subscription scope error`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )
        subscription.launch { error("Scope error") }

        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Configuration(subscribe.subscriptionId, null, false))

        val unsubscribe = clientChannel.receive() as LightstreamerClientMessage.Unsubscribe
        unsubscribe.subscriptionId `should be equal to` subscribe.subscriptionId
        subscription.exitStatus.join()
        subscription.exitStatus.getCompletionExceptionOrNull().`should not be null`()
        subscription.isClosedForReceive `should be equal to` true
    }

    @Test
    fun `subscription REQERR`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )

        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.RequestError(subscribe.id, 42, "error"))

        subscription.exitStatus.join()
        subscription.exitStatus.getCompletionExceptionOrNull().`should not be null`()
        subscription.isClosedForReceive `should be equal to` true
    }

    @Test
    fun `massive subscriptions`() = test {
        val updateDeferredList = (0..100_000).map { id ->
            val subscription = session.subscribe(
                SubscriptionMode.RAW,
                "dataAdapter$id",
                "group$id",
                LightstreamerSubscription.FieldList("f"),
                true
            )
            val updateDeferred = async { subscription.filterUpdate().receive() }

            val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
            subscribe.dataAdapterName `should be equal to` "dataAdapter$id"
            launch {
                serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
                serverChannel.send(
                    LightstreamerServerMessage.Update(
                        subscriptionId = subscribe.subscriptionId,
                        itemId = 1,
                        values = listOfValues(id.toString())
                    )
                )
            }

            id.toString() to updateDeferred
        }

        for ((value, updateDeferred) in updateDeferredList) {
            val update = updateDeferred.await()
            update["f"] `should be equal to` value
        }
    }

    @Test
    fun `massive early unsubscription`() = test(10_000) {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )
        subscription.cancel()
        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Configuration(subscribe.subscriptionId, null, false))

        val unsubscribe = clientChannel.receive() as LightstreamerClientMessage.Unsubscribe
        unsubscribe.subscriptionId `should be equal to` subscribe.subscriptionId
        subscription.exitStatus.join()
        subscription.isClosedForReceive `should be equal to` true
    }
}
