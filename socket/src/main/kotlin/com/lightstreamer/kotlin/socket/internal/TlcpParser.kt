package com.lightstreamer.kotlin.socket.internal

import com.lightstreamer.kotlin.socket.message.*

/**
 * A Lightstreamer TLCP parser for a single Lightstreamer connection.
 *
 * This implementation is not synchronized.
 */
internal class TlcpParser {

    private val buffer = FastCharSequence()

    private val itemList = ArrayList<String>(16)

    // 'update' must be parsed differently
    private var parsingUpdate = false

    fun parse(text: CharSequence) = sequence {
        for (c in text) {
            when (c) {
                '|' -> if (parsingUpdate && itemList.size >= 3) onItemEnd() else buffer.append(c)
                ',' -> if (parsingUpdate && itemList.size >= 3) buffer.append(c) else onItemEnd()
                '\r' -> yield(onLineEnd())
                '\n' -> check(itemList.isEmpty() && buffer.isEmpty()) { "Error parsing: \"$text\"" }
                else -> buffer.append(c)
            }
        }
    }

    private fun onItemEnd() {
        val item: String = when (buffer.length) {
            0 -> ""
            1 -> when (buffer[0]) {
                // some common values
                '#' -> "#"
                '$' -> "\$"
                '*' -> "*"
                '0' -> "0"
                '1' -> "1"
                'U' -> {
                    if (itemList.isEmpty()) parsingUpdate = true
                    "U"
                }

                else -> buffer.toString()
            }

            else -> buffer.toString()
        }
        itemList += item
        buffer.clear()
    }

    private fun onLineEnd(): LightstreamerServerMessage {
        onItemEnd()
        val serverMessage = LightstreamerServerMessage.parse(itemList)
        itemList.clear()
        parsingUpdate = false
        return serverMessage
    }
}
