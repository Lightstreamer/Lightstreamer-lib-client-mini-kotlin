package com.lightstreamer.client.internal

import com.lightstreamer.client.LightstreamerSubscription

/**
 * A subscription update.
 * [fields] and [values] are associated in positional way, so entries are `fields.zip(values)`.
 */
internal class SubscriptionUpdateMap(
    val fields: LightstreamerSubscription.FieldList,
    override val values: List<String?>
) : Map<String, String?> {

    override val entries: Set<Map.Entry<String, String?>>
        get() = object : AbstractSet<Map.Entry<String, String?>>() {
            override val size: Int get() = fields.size

            override fun iterator(): Iterator<Map.Entry<String, String?>> = iterator {
                for (i in fields.indices) {
                    yield(java.util.AbstractMap.SimpleImmutableEntry(fields[i], values[i]))
                }
            }
        }

    /**
     * Same as [fields]
     */
    override val keys: Set<String>
        get() = object : AbstractSet<String>() {

            override val size: Int get() = fields.size

            override fun contains(element: String): Boolean = this@SubscriptionUpdateMap.containsKey(element)

            override fun iterator(): Iterator<String> = fields.iterator()

            override fun toString(): String = fields.toString()
        }

    override val size: Int
        get() = fields.size

    init {
        require(fields.size == values.size)
    }

    override fun containsKey(key: String): Boolean = fields.contains(key)

    override fun containsValue(value: String?): Boolean = values.contains(value)

    override fun isEmpty(): Boolean = fields.isEmpty()

    override operator fun get(key: String): String? = values.getOrNull(fields.indexOf(key))

    override fun toString(): String =
        fields.zip(values).joinToString(prefix = "{", postfix = "}") { (f, v) -> "$f=$v" }
}
