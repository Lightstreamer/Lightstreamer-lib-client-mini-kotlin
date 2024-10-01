package com.lightstreamer.client

import com.lightstreamer.client.internal.LightstreamerSessionImpl
import com.lightstreamer.client.internal.TestSocket
import com.lightstreamer.client.internal.testSessionId
import com.lightstreamer.client.socket.LightstreamerServerAddress
import com.lightstreamer.client.socket.LightstreamerServerException
import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class CreateSessionTest {

    @Test
    fun `simple create`() {
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

            testSocket.testServer.send(
                LightstreamerServerMessage.ConnectionOk(
                    testSessionId, 0, 1.minutes, null
                )
            )
            val address: String = serverAddressQueue.poll(3, TimeUnit.SECONDS)
            address `should be equal to` "server.test"

            testSocket.testServer.send(LightstreamerServerMessage.End(1, "a"))
            val endError = session.exitStatus.await() as LightstreamerServerException.End
            endError.code `should be equal to` 1
            endError.description `should be equal to` "a"
        }
    }

    @Test
    fun `no force control link`() {
        runBlocking {
            val client =
                LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false), forceControlLink = false)
            val testSocketQueue = LinkedBlockingQueue<TestSocket>()
            LightstreamerSessionImpl(
                socketBuilder = { address ->
                    val testSocket = TestSocket(address.host)
                    testSocketQueue.add(testSocket)
                    testSocket
                },
                lightstreamerClient = client
            )

            val testSocket: TestSocket = testSocketQueue.poll(5, TimeUnit.SECONDS)
            testSocket.address `should be equal to` "server.test"
            val createSession = testSocket.testClient.receive() as LightstreamerClientMessage.CreateSession
            createSession.polling.shouldBeNull()

            testSocket.testServer.send(
                LightstreamerServerMessage.ConnectionOk(
                    testSessionId, 0, 1.minutes, "control-link.test"
                )
            )
            testSocket.testServer.send(LightstreamerServerMessage.Loop(Duration.ZERO))

            // do not close connection
            testSocketQueue.poll(3, TimeUnit.SECONDS).shouldBeNull()
        }
    }

    @Test
    fun `force control link`() {
        runBlocking {
            val client =
                LightstreamerClient(LightstreamerServerAddress("server.test", 80u, false), forceControlLink = true)
            val testSocketQueue = LinkedBlockingQueue<TestSocket>()
            LightstreamerSessionImpl(
                socketBuilder = { address ->
                    val testSocket = TestSocket(address.host)
                    testSocketQueue.add(testSocket)
                    testSocket
                },
                lightstreamerClient = client
            )

            var testSocket: TestSocket = testSocketQueue.poll(5, TimeUnit.SECONDS)
            testSocket.address `should be equal to` "server.test"
            val createSession = testSocket.testClient.receive() as LightstreamerClientMessage.CreateSession
            createSession.polling `should be equal to` Duration.ZERO

            testSocket.testServer.send(
                LightstreamerServerMessage.ConnectionOk(
                    testSessionId, 0, 1.minutes, "control-link.test"
                )
            )
            testSocket.testServer.send(LightstreamerServerMessage.Loop(Duration.ZERO))

            testSocket = testSocketQueue.poll(5, TimeUnit.SECONDS)
            testSocket.address `should be equal to` "control-link.test"
            testSocket.testClient.receive() is LightstreamerClientMessage.BindSession
            testSocket.testClient.isClosedForSend `should be equal to` false
        }
    }
}
