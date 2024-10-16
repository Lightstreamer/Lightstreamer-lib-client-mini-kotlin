package com.lightstreamer.kotlin.socket

import java.io.IOException

/**
 * Server error
 */
public sealed class LightstreamerServerException(
    public val code: Int,
    public val description: String,
    cause: Throwable?
) : IOException("$code: $description", cause) {

    /**
     * [com.lightstreamer.client.socket.message.LightstreamerServerMessage.ConnectionError] message received
     */
    public class ConnectionError(code: Int, description: String, cause: Throwable? = null) :
        LightstreamerServerException(code, description, cause)

    /**
     * [com.lightstreamer.client.socket.message.LightstreamerServerMessage.End] message received
     */
    public class End(code: Int, description: String, cause: Throwable? = null) :
        LightstreamerServerException(code, description, cause)

    /**
     * [com.lightstreamer.client.socket.message.LightstreamerServerMessage.Error] message received
     */
    public class Error(code: Int, description: String, cause: Throwable? = null) :
        LightstreamerServerException(code, description, cause)

    /**
     * [com.lightstreamer.client.socket.message.LightstreamerServerMessage.RequestError] message received
     */
    public class RequestError(code: Int, description: String, cause: Throwable? = null) :
        LightstreamerServerException(code, description, cause)

}
