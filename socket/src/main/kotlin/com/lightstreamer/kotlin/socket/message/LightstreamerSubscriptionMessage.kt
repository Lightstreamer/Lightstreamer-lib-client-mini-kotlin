/*
 * Copyright (C) 2024 Lightstreamer Srl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
