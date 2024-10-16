package com.lightstreamer.kotlin.client

import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.Deferred

public interface LightstreamerSession {
    public val exitStatus: Deferred<LightstreamerServerException?>

    public val sessionId: Deferred<String>

    public suspend fun sendMessage(message: String, sequenceName: String? = null): LightstreamerMessageResponse

    public fun subscribe(
        mode: SubscriptionMode,
        dataAdapterName: String,
        itemGroup: String,
        itemFields: LightstreamerSubscription.FieldList,
        requestSnapshot: Boolean = mode.supportSnapshot
    ): LightstreamerSubscription

    public fun close()
}
