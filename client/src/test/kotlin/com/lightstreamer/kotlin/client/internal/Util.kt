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
package com.lightstreamer.kotlin.client.internal

import com.lightstreamer.kotlin.client.*
import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.amshove.kluent.`should be equal to`
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

const val testSessionId = "testSessionId"

fun listOfValues(vararg values: String?) = listOf(*values).map { LightstreamerServerMessage.Update.Value.Text(it) }

fun LightstreamerSubscription.filterUpdate() = GlobalScope.produce(capacity = Channel.UNLIMITED) {
    consumeEach { message ->
        if (message is LightstreamerSubscriptionMessage.Update)
            send(message)
    }
}

internal fun test(repeat: Int = 1, block: suspend TestScope.() -> Unit) {
    runBlocking {
        repeat(repeat) {
            val testScope = TestScope(CoroutineScope(Dispatchers.Default))
            with(testScope) {
                clientChannel.receive() as LightstreamerClientMessage.CreateSession
                serverChannel.send(
                    LightstreamerServerMessage.ConnectionOk(testSessionId, 0, 1.minutes, null)
                )
                try {
                    withTimeout(TimeUnit.MINUTES.toMillis(1)) {
                        block()
                    }
                } finally {
                    session.close()
                }
            }
            testScope.cancel("Test completed")
        }
    }
}

internal class TestScope(coroutineScope: CoroutineScope) : CoroutineScope by coroutineScope {

    @Volatile
    private var testSocket = TestSocket()

    val serverChannel: SendChannel<LightstreamerServerMessage> get() = testSocket.testServer
    val clientChannel: ReceiveChannel<LightstreamerClientMessage> get() = testSocket.testClient

    val client = LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false))

    val session: LightstreamerSession = LightstreamerSessionImpl(
        socketBuilder = { testSocket },
        lightstreamerClient = client
    )

    suspend fun resetConnection(): LightstreamerClientMessage.BindSession {
        val oldTestSocket = testSocket
        testSocket = TestSocket()
        oldTestSocket.disconnect()
        val bindSession = clientChannel.receive() as LightstreamerClientMessage.BindSession
        bindSession.session `should be equal to` "testSessionId"
        serverChannel.send(LightstreamerServerMessage.ConnectionOk("testSessionId", 0, 1.minutes, null))
        return bindSession
    }
}


class TestSocket(
    val address: String = "",
    val testServer: Channel<LightstreamerServerMessage> = Channel(Channel.UNLIMITED)
) : LightstreamerSocket, ReceiveChannel<LightstreamerServerMessage> by testServer {

    private val closeDeferred = CompletableDeferred<Unit>()

    val testClient = Channel<LightstreamerClientMessage>(Channel.UNLIMITED)

    override fun disconnect() {
        testServer.close()
        testClient.close()
        closeDeferred.complete(Unit)
    }

    override suspend fun join() = closeDeferred.join()

    override suspend fun send(message: LightstreamerClientMessage) {
        testClient.send(message)
    }
}
