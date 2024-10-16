package com.lightstreamer.kotlin.client

import com.lightstreamer.kotlin.client.internal.LightstreamerSessionImpl
import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.http.HttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * A [LightstreamerSession] factory
 *
 * @param keepAlive Longest inactivity time allowed for the connection.
 * If not present, the keep-alive time is decided by the Server, based on its own configuration.
 *
 * @param keepAliveExtra extra time to wait after last PROBE
 *
 * @param inactivity Maximum time the Client is committed to wait before issuing a request to the Server while this connection is open.
 * Each session automatically send heartbeat package to keep the connection alive.
 *
 * @param forceControlLink if control link is present the first session is redirected on it
 */
public class LightstreamerClient(
    public val serverAddress: LightstreamerServerAddress,
    public val adapterSetName: String = "DEFAULT",
    internal val userCredential: UsernamePassword? = null,
    public val keepAlive: Duration? = null,
    public val keepAliveExtra: Duration = 3.seconds,
    public val inactivity: Duration? = null,
    public val forceControlLink: Boolean = false,
    public val createSessionTTL: LightstreamerClientMessage.CreateSession.TTL = LightstreamerClientMessage.CreateSession.TTL.UNKNOWN,
) {

    init {
        require(adapterSetName.isNotEmpty()) { "Empty adapter set name" }
        require(keepAlive == null || keepAlive.isPositive()) { "Invalid keepAlive $keepAlive" }
        require(inactivity == null || inactivity.isPositive()) { "Invalid inactivity $inactivity" }
    }

    internal val job: Job = SupervisorJob()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(keepAliveExtra.coerceAtLeast(1.seconds).toJavaDuration())
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    init {
        job.invokeOnCompletion { httpClient.shutdownNow() }
    }

    public fun openSession(): LightstreamerSession {
        check(job.isActive) { "Client is closed" }
        return LightstreamerSessionImpl(
            socketBuilder = { address -> LightstreamerTlcpSocket.connect(address, httpClient) },
            lightstreamerClient = this
        )
    }

    public fun isClosed(): Boolean = job.isCompleted

    /**
     * Close this client, all sessions, and disposes resources.
     */
    public fun close() {
        job.cancel("Client of $adapterSetName on $serverAddress has been closed")
    }

    override fun toString(): String = "$adapterSetName@$serverAddress"
}
