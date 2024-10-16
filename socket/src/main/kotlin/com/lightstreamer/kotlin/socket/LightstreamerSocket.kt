package com.lightstreamer.kotlin.socket

import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.channels.ReceiveChannel

public interface LightstreamerSocket : ReceiveChannel<LightstreamerServerMessage> {

    public fun disconnect()

    public suspend fun join()

    public suspend fun send(message: LightstreamerClientMessage)
}
