package com.lightstreamer.client.socket

/**
 * Allowed subscription mode.
 *
 * @param supportSnapshot true if snapshot is supported
 * @param supportEndOfSnapshot true if protocol support the end of snapshot, otherwise it is implicit (MERGE) or unsupported (RAW)
 */
@Suppress("unused")
public enum class SubscriptionMode(public val supportSnapshot: Boolean, public val supportEndOfSnapshot: Boolean) {
    COMMAND(true, true),
    DISTINCT(true, true),
    MERGE(true, false),
    RAW(false, false)
}
