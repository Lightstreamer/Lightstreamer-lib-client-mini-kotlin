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
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be instance of`
import org.junit.jupiter.api.Test

class MessageTest {

    @Test
    fun `message ok`() = test {
        val messageDeferred = async { session.sendMessage("testMessage") }
        val message = clientChannel.receive() as LightstreamerClientMessage.Message
        message.message `should be equal to` "testMessage"

        messageDeferred.isCompleted `should be equal to` false
        serverChannel.send(LightstreamerServerMessage.MessageDone(null, message.msgProg!!, "OK"))
        messageDeferred.await() `should be equal to` LightstreamerMessageResponse.Done("OK")
    }

    @Test
    fun `message error`() = test {
        val messageDeferred = async { session.sendMessage("testMessage") }
        val message = clientChannel.receive() as LightstreamerClientMessage.Message
        message.message `should be equal to` "testMessage"

        messageDeferred.isCompleted `should be equal to` false
        serverChannel.send(LightstreamerServerMessage.MessageFail(null, message.msgProg!!, 42, "messageError"))
        messageDeferred.await() `should be equal to` LightstreamerMessageResponse.Fail(42, "messageError")
    }

    @Test
    fun `close while message`() = test(1_000) {
        session.sessionId.await()
        val messageDeferred = async { runCatching { session.sendMessage("testMessage") } }
        session.close()
        val result = messageDeferred.await()
        result.exceptionOrNull() `should be instance of` CancellationException::class
    }
}
