package com.lightstreamer.kotlin.client.internal

import com.lightstreamer.kotlin.client.*
import com.lightstreamer.kotlin.socket.diff.TlcpDiffDecoder
import com.lightstreamer.kotlin.socket.message.*

/**
 * Calculate values for each updates
 */
internal class SubscriptionUpdateProcessor(
    itemCount: UShort,
    fieldCount: UShort,
    private val itemFields: LightstreamerSubscription.FieldList
) {

    private val values: Array<String?> =
        if (fieldCount == 0.toUShort()) EMPTY_FIELD_ARRAY else arrayOfNulls(fieldCount.toInt())

    init {
        require(itemCount >= 0u) { "item count must be positive, it is $itemCount" }
        require(itemCount <= 1u) { "item count cannot exceed 1, it is $itemCount" }
    }

    operator fun invoke(message: LightstreamerServerMessage.Update): LightstreamerSubscriptionMessage.Update {
        var i = 0
        for (value in message.values) {
            when (value) {
                is LightstreamerServerMessage.Update.Value.Text -> {
                    values[i] = value.content
                    i++
                }

                is LightstreamerServerMessage.Update.Value.Unchanged ->
                    i += value.count.toInt()

                is LightstreamerServerMessage.Update.Value.Patch -> {
                    val previousValue = requireNotNull(values[i]) { "Value $i was null, unable to apply patch: $value" }
                    values[i] = TlcpDiffDecoder.patch(previousValue, value)
                    i++
                }
            }
        }
        return LightstreamerSubscriptionMessage.Update(SubscriptionUpdateMap(itemFields, values.toList()))
    }

    private companion object {
        private val EMPTY_FIELD_ARRAY = emptyArray<String?>()
    }
}
