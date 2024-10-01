package com.lightstreamer.client.socket

import com.lightstreamer.client.socket.message.LightstreamerClientMessage
import com.lightstreamer.client.socket.message.LightstreamerServerMessage
import kotlinx.coroutines.channels.ReceiveChannel

public interface LightstreamerSocket : ReceiveChannel<LightstreamerServerMessage> {

    public fun disconnect()

    public suspend fun join()

    public suspend fun send(message: LightstreamerClientMessage)
}
