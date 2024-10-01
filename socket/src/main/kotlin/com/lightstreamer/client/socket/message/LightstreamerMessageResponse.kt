package com.lightstreamer.client.socket.message

public sealed class LightstreamerMessageResponse {

    public data class Done(val response: String) : LightstreamerMessageResponse()

    public data class Fail(val code: Int, val message: String) : LightstreamerMessageResponse()
}
