package com.lightstreamer.kotlin.socket.message

/**
 * Subscription event
 */
public sealed class LightstreamerSubscriptionMessage {

    public data class ClearSnapshot(val itemId: Int) : LightstreamerSubscriptionMessage()

    public data class Configuration(val maxFrequency: Float?, val filtered: Boolean) :
        LightstreamerSubscriptionMessage()

    public data class EndOfSnapshot(val itemId: Int) : LightstreamerSubscriptionMessage()

    public data class Overflow(val item: Int, val overflowSize: Int) : LightstreamerSubscriptionMessage()

    public class Update(private val updateMap: Map<String, String?>) :
        LightstreamerSubscriptionMessage(), Map<String, String?> by updateMap {
        override fun hashCode(): Int = updateMap.hashCode()
        override fun equals(other: Any?): Boolean = this === other || (other is Update) && updateMap == other.updateMap
        override fun toString(): String = "Update$updateMap"
    }

    public data class SubscriptionOk(
        val itemCount: UShort,
        val fieldCount: UShort,
        val keyFieldPos: UShort? = null,
        val commandFieldPos: UShort? = null
    ) : LightstreamerSubscriptionMessage()
}
