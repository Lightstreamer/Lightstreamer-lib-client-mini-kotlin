/*
 * Copyright (C) 2024 Lightstreamer Srl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lightstreamer.kotlin.client

import com.lightstreamer.kotlin.client.internal.*
import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.whileSelect
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be instance of`
import org.junit.jupiter.api.Test

class SessionRecoveryTest {

    @Test
    fun `recover subscription`() = test {
        val updateChannel =
            session.subscribe(
                SubscriptionMode.RAW,
                "dataAdapter",
                "group",
                LightstreamerSubscription.FieldList("f"),
                requestSnapshot = true
            )
                .filterUpdate()

        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.RequestOk(subscribe.id))
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Update(subscribe.subscriptionId, 1, listOfValues("1")))
        serverChannel.send(LightstreamerServerMessage.Update(subscribe.subscriptionId, 1, listOfValues("2")))

        var update = updateChannel.receive()
        update["f"] `should be equal to` "1"
        update = updateChannel.receive()
        update["f"] `should be equal to` "2"

        val bindSession = resetConnection()
        bindSession.recoveryFrom `should be equal to` 3
        serverChannel.send(LightstreamerServerMessage.Progressive(2))
        serverChannel.send(LightstreamerServerMessage.Update(subscribe.subscriptionId, 1, listOfValues("2")))
        serverChannel.send(LightstreamerServerMessage.Update(subscribe.subscriptionId, 1, listOfValues("3")))
        update = updateChannel.receive()
        update["f"] `should be equal to` "3"
    }

    @Test
    fun `recover unsubscription`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )

        subscription.cancel()
        clientChannel.receive() as LightstreamerClientMessage.Subscribe // lost subscribe

        delay(100L)
        resetConnection()
        serverChannel.send(LightstreamerServerMessage.Progressive(0))
        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        val unsubscribe = clientChannel.receive() as LightstreamerClientMessage.Unsubscribe
        unsubscribe.subscriptionId `should be equal to` subscribe.subscriptionId
        serverChannel.send(LightstreamerServerMessage.RequestOk(unsubscribe.id))
        serverChannel.send(LightstreamerServerMessage.UnsubscriptionOk(subscribe.subscriptionId))
    }

    @Test
    fun `recover send message`() = test {
        val messageDeferred = async { session.sendMessage("testMessage") }
        clientChannel.receive() as LightstreamerClientMessage.Message // lost message

        val bindSession = resetConnection()
        bindSession.recoveryFrom `should be equal to` 0
        serverChannel.send(LightstreamerServerMessage.Progressive(0))
        val message = clientChannel.receive() as LightstreamerClientMessage.Message
        message.message `should be equal to` "testMessage"

        messageDeferred.isCompleted `should be equal to` false
        serverChannel.send(LightstreamerServerMessage.MessageDone(null, message.msgProg!!, "OK"))
        messageDeferred.await() `should be equal to` LightstreamerMessageResponse.Done("OK")
    }

    @Test
    fun `do not recover cancelled send message`() = test {
        val messageDeferred = async { session.sendMessage("testMessage") }
        clientChannel.receive() as LightstreamerClientMessage.Message // lost message
        messageDeferred.cancelAndJoin()
        delay(3)

        val bindSession = resetConnection()
        bindSession.recoveryFrom `should be equal to` 0
        serverChannel.send(LightstreamerServerMessage.Progressive(0))
        whileSelect {
            clientChannel.onReceive { message ->
                message `should not be instance of` LightstreamerClientMessage.Message::class
                true
            }
            onTimeout(200) {
                false
            }
        }
    }
}
