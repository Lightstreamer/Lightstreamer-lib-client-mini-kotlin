package com.lightstreamer.client

import com.lightstreamer.client.internal.filterUpdate
import com.lightstreamer.client.internal.listOfValues
import com.lightstreamer.client.internal.test
import com.lightstreamer.client.socket.SubscriptionMode
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

class UpdateTest {

    @Test
    fun `update with special characters`() = test {
        val updateDeferred = async {
            session.subscribe(
                SubscriptionMode.RAW,
                "dataAdapter",
                "group",
                LightstreamerSubscription.FieldList("f"),
                true
            )
                .filterUpdate().consumeAsFlow()
                .first()
        }

        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.RequestOk(subscribe.id))
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Update(subscribe.subscriptionId, 1, listOfValues("a +b%20%2Bc,")))

        val update = updateDeferred.await()
        update["f"] `should be equal to` "a +b%20%2Bc,"
    }

    @Test
    fun `update with unchanged values`() = test {
        val updateChannel =
            session.subscribe(
                SubscriptionMode.MERGE,
                "dataAdapter",
                "group",
                LightstreamerSubscription.FieldList("a", "b", "c"),
                true
            )
                .filterUpdate().consumeAsFlow()
                .produceIn(this)

        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.RequestOk(subscribe.id))
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 3u))
        serverChannel.send(
            LightstreamerServerMessage.Update(
                subscribe.subscriptionId, 1, listOfValues("a1", "b1", "c1")
            )
        )

        val update1 = updateChannel.receive()
        update1["a"] `should be equal to` "a1"
        update1["b"] `should be equal to` "b1"
        update1["c"] `should be equal to` "c1"

        serverChannel.send(
            LightstreamerServerMessage.Update(
                subscribe.subscriptionId, 1, listOf(
                    LightstreamerServerMessage.Update.Value.Text("a2"),
                    LightstreamerServerMessage.Update.Value.Unchanged(2u)
                )
            )
        )

        val update2 = updateChannel.receive()
        update2["a"] `should be equal to` "a2"
        update2["b"] `should be equal to` "b1"
        update2["c"] `should be equal to` "c1"

        serverChannel.send(
            LightstreamerServerMessage.Update(
                subscribe.subscriptionId, 1, listOf(
                    LightstreamerServerMessage.Update.Value.Unchanged(1u),
                    LightstreamerServerMessage.Update.Value.Text("b3"),
                    LightstreamerServerMessage.Update.Value.Text("c3")
                )
            )
        )

        val update3 = updateChannel.receive()
        update3["a"] `should be equal to` "a2"
        update3["b"] `should be equal to` "b3"
        update3["c"] `should be equal to` "c3"
    }
}
