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
