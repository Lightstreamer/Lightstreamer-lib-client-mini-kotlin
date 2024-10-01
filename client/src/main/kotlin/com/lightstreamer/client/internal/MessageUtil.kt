package com.lightstreamer.client.internal

import com.lightstreamer.client.socket.message.LightstreamerServerMessage

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
