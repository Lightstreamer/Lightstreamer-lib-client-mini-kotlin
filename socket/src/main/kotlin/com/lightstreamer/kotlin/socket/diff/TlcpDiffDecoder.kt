package com.lightstreamer.kotlin.socket.diff

import com.lightstreamer.kotlin.socket.message.*

public object TlcpDiffDecoder {

    public fun patch(source: CharSequence, update: LightstreamerServerMessage.Update.Value.Patch): String {
        require(update.diffFormat == SupportedDiff.TLCPDiff) { "Unsupported diff format ${update.diffFormat}" }
        val newStringLength = calculatePatchResultStringLength(update)
        val sb = StringBuilder(newStringLength)
        val patchIterator = PatchIterator(update.diff)
        val sourceIterator = PatchIterator(source)
        while (true) {
            // decode Copy
            if (!patchIterator.hasNext()) break
            var varint = patchIterator.decodeVarint()
            sourceIterator.nextCharsTo(varint, sb)

            // decode Add
            if (!patchIterator.hasNext()) break
            varint = patchIterator.decodeVarint()
            sb.ensureCapacity(sb.length + varint)
            patchIterator.nextCharsTo(varint, sb)

            // decode Delete
            if (!patchIterator.hasNext()) break
            varint = patchIterator.decodeVarint()
            sourceIterator.skip(varint)
        }
        check(sb.length == newStringLength)
        return sb.toString()
    }

    private fun calculatePatchResultStringLength(update: LightstreamerServerMessage.Update.Value.Patch): Int {
        val patchIterator = PatchIterator(update.diff)
        var length = 0
        while (true) {
            // Copy length
            if (!patchIterator.hasNext()) break
            var varint = patchIterator.decodeVarint()
            length += varint

            // Add length
            if (!patchIterator.hasNext()) break
            varint = patchIterator.decodeVarint()
            // ignore Add
            patchIterator.skip(varint)
            length += varint

            // ignore Delete
            if (!patchIterator.hasNext()) break
            patchIterator.decodeVarint()
        }
        return length
    }
}

private const val VARINT_RADIX = 'z'.code - 'a'.code + 1

private class PatchIterator(private val diff: CharSequence) : CharIterator() {

    private var i: Int = 0

    override fun hasNext(): Boolean = i < diff.length

    override fun nextChar(): Char {
        try {
            val char = diff[i]
            i++
            return char
        } catch (e: IndexOutOfBoundsException) {
            throw NoSuchElementException()
        }
    }

    fun nextCharsTo(n: Int, stringBuilder: StringBuilder) {
        if (n == 0) return
        val end = i + n
        stringBuilder.append(diff, i, end)
        i = end
    }

    fun skip(n: Int) {
        i += n
    }
}

private fun CharIterator.decodeVarint(): Int {
    var res = 0
    while (true) {
        res *= VARINT_RADIX
        when (val c = nextChar()) {
            in 'a'..'z' -> {
                // last char
                res += c.code - 'a'.code
                // stop parsing when a lowercase char has been read
                return res
            }

            in 'A'..'Z' -> res += c.code - 'A'.code

            else -> error("Malformed input string: '$c'")
        }
    }
}
