package com.lightstreamer.client.internal

import com.lightstreamer.client.LightstreamerClient
import com.lightstreamer.client.LightstreamerSession
import com.lightstreamer.client.LightstreamerSubscription
import com.lightstreamer.client.socket.LightstreamerServerAddress
import com.lightstreamer.client.socket.LightstreamerSocket
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import com.lightstreamer.client.socket.message.LightstreamerSubscriptionMessage
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
