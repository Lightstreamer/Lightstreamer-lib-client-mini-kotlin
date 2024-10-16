package com.lightstreamer.kotlin.client.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generate incremental message progressive holder.
 *
 * Unordered message with ack should get a progressive id.
 */
internal class MessageSequenceProgressiveHolder {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>(8)

    operator fun get(sequenceName: String): Int =
        sequences.computeIfAbsent(sequenceName) { AtomicInteger() }
            .incrementAndGet()
}
