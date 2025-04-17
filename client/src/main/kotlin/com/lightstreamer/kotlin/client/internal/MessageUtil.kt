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
package com.lightstreamer.kotlin.client.internal

import com.lightstreamer.kotlin.socket.message.*

/**
 * TLCP specification:
 *
 * More precisely, the notifications to be counted are the ones related with the subscription and
 * message activity (as reported below in Real-Time Update, Other Subscription-Related
 * Notifications, and Message-Related Notifications). We will call them “data notifications”.
 */
internal fun LightstreamerServerMessage.isDataNotification() =
    this is LightstreamerServerMessage.SubscriptionServerMessage
            || this is LightstreamerServerMessage.MessageResultServerMessage
