package com.lightstreamer.kotlin.client

import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.message.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.*

public interface LightstreamerSubscription : CoroutineScope, ReceiveChannel<LightstreamerSubscriptionMessage> {

    public val mode: SubscriptionMode

    public val dataAdapterName: String

    public val itemGroup: String

    public val itemFields: FieldList

    public val exitStatus: Deferred<Unit>

    /**
     * A sorted unique list implementing a `log(n)` [indexOf] and [contains].
     */
    public class FieldList(fieldNames: Collection<String>) : AbstractList<String>(), Set<String> {
        private val fields = fieldNames.toSortedSet(itemNameComparator).toTypedArray()

        public constructor(vararg fields: String) : this(fieldNames = fields.asList())

        override val size: Int
            get() = fields.size

        override fun get(index: Int): String = fields[index]

        override fun indexOf(element: String): Int = Arrays.binarySearch(fields, element, itemNameComparator)

        override fun contains(element: String): Boolean = indexOf(element) >= 0

        override fun spliterator(): Spliterator<String> = Arrays.spliterator(fields)

        private companion object {
            val itemNameComparator = Comparator<String> { o1, o2 ->
                var res = o1.hashCode() - o2.hashCode()
                if (res == 0) res = o1.compareTo(o2)
                res
            }
        }
    }
}
