package com.lightstreamer.client

import com.lightstreamer.client.socket.LightstreamerServerException
import kotlin.coroutines.cancellation.CancellationException

public class LightstreamerCancellationException(public val serverException: LightstreamerServerException) :
    CancellationException(serverException.message) {

    init {
        initCause(serverException)
    }
}
