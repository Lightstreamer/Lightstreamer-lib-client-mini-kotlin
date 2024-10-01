package com.lightstreamer.client.socket.internal

/**
 * A fast, not synchronized appendable, resettable char sequence.
 */
internal class FastCharSequence(initialSize: Int = 64) : Appendable, CharSequence, Iterable<Char> {

    private var chars = CharArray(initialSize)

    override var length: Int = 0
        private set

    override fun append(csq: CharSequence): Appendable = append(csq, 0, csq.length)

    override fun append(csq: CharSequence, start: Int, end: Int): Appendable {
        if (start !in 0..end) throw IndexOutOfBoundsException("start=$start, end=$end")
        ensureCapacity(length + (end - start))
        for (i in start..<end) {
            chars[length] = csq[i]
            length++
        }
        return this
    }

    override fun append(c: Char): Appendable {
        val newLength = length + 1
        ensureCapacity(newLength)
        chars[length] = c
        length = newLength
        return this
    }

    override fun get(index: Int): Char {
        require(index < length) { "index $index>=$length" }
        return chars[index]
    }

    override fun iterator(): CharIterator = object : CharIterator() {
        private var i = 0
        override fun hasNext(): Boolean = i < length
        override fun nextChar(): Char = if (hasNext()) chars[i++] else error("No more elements")
    }

    fun clear() {
        length = 0
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (endIndex >= length) throw StringIndexOutOfBoundsException("endIndex $endIndex>=$length")
        return String(chars, startIndex, endIndex - startIndex)
    }

    override fun toString(): String = String(chars, 0, length)

    private fun ensureCapacity(newCapacity: Int) {
        if (newCapacity > chars.size) {
            chars = chars.copyOf(newCapacity.takeHighestOneBit().shl(1))
        }
    }
}
