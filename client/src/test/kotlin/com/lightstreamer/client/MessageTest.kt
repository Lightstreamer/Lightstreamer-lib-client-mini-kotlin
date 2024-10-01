package com.lightstreamer.client

import com.lightstreamer.client.internal.test
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerMessageResponse
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
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
