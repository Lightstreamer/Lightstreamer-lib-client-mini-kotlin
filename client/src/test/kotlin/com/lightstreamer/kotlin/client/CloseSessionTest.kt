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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue

class CloseSessionTest {

    @Test
    fun `close session before create`() {
        runBlocking {
            val client = LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false))
            val serverAddressQueue = LinkedBlockingQueue<String>()
            val testSocket = TestSocket()
            val session = LightstreamerSessionImpl(
                socketBuilder = { address ->
                    serverAddressQueue.add(address.host)
                    testSocket
                },
                lightstreamerClient = client
            )

            session.close()
            session.exitStatus.join()
            client.isClosed() `should be equal to` false
        }
    }

    @Test
    fun `close session after create`() {
        runBlocking {
            val client = LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false))
            val serverAddressQueue = LinkedBlockingQueue<String>()
            val testSocket = TestSocket()
            val session = LightstreamerSessionImpl(
                socketBuilder = { address ->
                    serverAddressQueue.add(address.host)
                    testSocket
                },
                lightstreamerClient = client
            )

            val createSession = testSocket.testClient.receive() as LightstreamerClientMessage.CreateSession
            createSession.polling.shouldBeNull()

            session.close()
            session.exitStatus.join()
            client.isClosed() `should be equal to` false
        }
    }

    @Test
    fun `close client before create`() {
        runBlocking {
            val client = LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false))
            val serverAddressQueue = LinkedBlockingQueue<String>()
            val testSocket = TestSocket()
            val session = LightstreamerSessionImpl(
                socketBuilder = { address ->
                    serverAddressQueue.add(address.host)
                    testSocket
                },
                lightstreamerClient = client
            )

            client.close()
            session.exitStatus.join()
        }
    }

    @Test
    fun `close client after create`() {
        runBlocking {
            val client = LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false))
            val serverAddressQueue = LinkedBlockingQueue<String>()
            val testSocket = TestSocket()
            val session = LightstreamerSessionImpl(
                socketBuilder = { address ->
                    serverAddressQueue.add(address.host)
                    testSocket
                },
                lightstreamerClient = client
            )

            val createSession = testSocket.testClient.receive() as LightstreamerClientMessage.CreateSession
            createSession.polling.shouldBeNull()

            client.close()
            session.exitStatus.join()
            session.exitStatus.getCompletionExceptionOrNull() `should be instance of` CancellationException::class
        }
    }

    @Test
    fun `close after subscribe`() = test {
        val subscription = session.subscribe(
            SubscriptionMode.RAW,
            "dataAdapter",
            "group",
            LightstreamerSubscription.FieldList("f"),
            requestSnapshot = true
        )
        val subscribe = clientChannel.receive() as LightstreamerClientMessage.Subscribe

        serverChannel.send(LightstreamerServerMessage.RequestOk(subscribe.id))
        serverChannel.send(LightstreamerServerMessage.SubscriptionOk(subscribe.subscriptionId, 1u, 1u))
        serverChannel.send(LightstreamerServerMessage.Configuration(subscribe.subscriptionId, null, false))

        client.close()
        subscription.exitStatus.join()
        subscription.exitStatus.getCompletionExceptionOrNull() `should be instance of` CancellationException::class
        subscription.isClosedForReceive `should be equal to` true
    }
}
