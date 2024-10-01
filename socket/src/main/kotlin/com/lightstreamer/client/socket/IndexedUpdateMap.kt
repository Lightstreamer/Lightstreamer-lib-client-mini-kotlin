package com.lightstreamer.client.socket

/**
 * A server update that associate each `(key,value)` pairs to an index.
 */
public interface IndexedUpdateMap : Map<String, String?> {

    /**
     * Return the index of the [key] or -1 if it is not present.
     */
    public fun getKeyIndex(key: String): Int

    public fun getKey(index: Int): String?

    public fun getValue(index: Int): String?
}
