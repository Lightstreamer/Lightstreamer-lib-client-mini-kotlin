package com.lightstreamer.kotlin.client

import com.lightstreamer.kotlin.socket.*
import kotlin.coroutines.cancellation.CancellationException

public class LightstreamerCancellationException(public val serverException: LightstreamerServerException) :
    CancellationException(serverException.message) {

    init {
        initCause(serverException)
    }
}
