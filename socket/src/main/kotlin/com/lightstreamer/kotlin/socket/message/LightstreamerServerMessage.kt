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
package com.lightstreamer.kotlin.socket.message

import com.lightstreamer.kotlin.socket.diff.SupportedDiff
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Define server messages
 */
public sealed class LightstreamerServerMessage {

    public data class ClearSnapshot(override val subscriptionId: Int, val itemId: Int) : LightstreamerServerMessage(),
        SubscriptionServerMessage

    public data class ClientIp(val clientIp: String) : LightstreamerServerMessage()

    public data class Configuration(override val subscriptionId: Int, val maxFrequency: Float?, val filtered: Boolean) :
        LightstreamerServerMessage(), SubscriptionServerMessage

    public data class ConnectionOk(
        val sessionId: String,
        val requestLimit: Int,
        val keepAlive: Duration?,
        val controlLink: String?
    ) : LightstreamerServerMessage()

    public data class ConnectionError(val code: Int, val message: String) : LightstreamerServerMessage()

    public data class Constraint(val bandwidth: Float?) : LightstreamerServerMessage()

    public data class Error(val code: Int, val message: String) : LightstreamerServerMessage()

    public data class End(val code: Int, val message: String) : LightstreamerServerMessage()

    public data class EndOfSnapshot(override val subscriptionId: Int, val itemId: Int) : LightstreamerServerMessage(),
        SubscriptionServerMessage

    public data class Loop(val expectedDelay: Duration) : LightstreamerServerMessage()

    public data class MessageDone(override val sequence: String?, override val prog: Int, val response: String) :
        LightstreamerServerMessage(), MessageResultServerMessage

    public data class MessageFail(
        override val sequence: String?,
        override val prog: Int,
        val errorCode: Int,
        val errorMessage: String
    ) : LightstreamerServerMessage(), MessageResultServerMessage

    public data object NoOp : LightstreamerServerMessage()

    public data class Overflow(override val subscriptionId: Int, val item: Int, val overflowSize: Int) :
        LightstreamerServerMessage(), SubscriptionServerMessage

    public data object Probe : LightstreamerServerMessage()

    public data class Progressive(val progressive: Int) : LightstreamerServerMessage()

    public data class RequestOk(override val requestId: String) : LightstreamerServerMessage(), RequestServerMessage

    public data class RequestError(override val requestId: String, val code: Int, val message: String) :
        LightstreamerServerMessage(), RequestServerMessage

    public data class Synchronize(val secondsSinceInitialHeader: Int) : LightstreamerServerMessage()

    public data class SubscriptionOk(
        override val subscriptionId: Int,
        override val itemCount: UShort,
        override val fieldCount: UShort
    ) : LightstreamerServerMessage(), SubscriptionOkServerMessage

    public data class SubscriptionCommandOk(
        override val subscriptionId: Int,
        override val itemCount: UShort,
        override val fieldCount: UShort,
        val keyFieldPos: UShort,
        val commandFieldPos: UShort
    ) : LightstreamerServerMessage(), SubscriptionOkServerMessage

    public data class ServerName(val serverName: String) : LightstreamerServerMessage()

    public data class UnsubscriptionOk(override val subscriptionId: Int) : LightstreamerServerMessage(),
        SubscriptionServerMessage

    public data class Update(override val subscriptionId: Int, val itemId: Int, val values: List<Value>) :
        LightstreamerServerMessage(), SubscriptionServerMessage {
        public sealed class Value {
            public data class Patch(val diffFormat: SupportedDiff, val diff: String) : Value()
            public data class Text(val content: String?) : Value()
            public data class Unchanged(val count: UShort) : Value()
        }
    }

    public sealed interface MessageResultServerMessage {
        public val sequence: String?
        public val prog: Int
    }

    public sealed interface RequestServerMessage {
        public val requestId: String
    }

    public sealed interface SubscriptionServerMessage {
        public val subscriptionId: Int
    }

    public sealed interface SubscriptionOkServerMessage : SubscriptionServerMessage {
        public val itemCount: UShort
        public val fieldCount: UShort
    }

    internal companion object {

        private val EMPTY_TEXT_VALUE = Update.Value.Text("")

        private val NULL_TEXT_VALUE = Update.Value.Text(null)

        private val UNCHANGED_VALUE = Update.Value.Unchanged(1u)

        internal fun parse(line: List<String>): LightstreamerServerMessage =
            when (line[0]) {
                "CLIENTIP" -> ClientIp(clientIp = decodeTlcp(line[1]))

                "CONF" -> Configuration(
                    subscriptionId = line[1].toInt(),
                    maxFrequency = line[2].toFloatOrNull(),
                    filtered = line[3].length == 8
                )

                "CONS" -> Constraint(bandwidth = line[1].toFloatOrNull())

                "CONERR" -> ConnectionError(code = line[1].toInt(), message = decodeTlcp(line[2]))

                "CONOK" -> ConnectionOk(
                    sessionId = line[1],
                    requestLimit = line[2].toInt(),
                    keepAlive = line[3].toLong().milliseconds,
                    controlLink = line[4].takeUnless { it == "*" }
                )

                "CS" -> ClearSnapshot(subscriptionId = line[1].toInt(), itemId = line[2].toInt())

                "END" -> End(code = line[1].toInt(), message = decodeTlcp(line[2]))

                "EOS" -> EndOfSnapshot(subscriptionId = line[1].toInt(), itemId = line[2].toInt())

                "ERROR" -> Error(code = line[1].toInt(), message = decodeTlcp(line[2]))

                "LOOP" -> Loop(expectedDelay = line[1].toLong().milliseconds)

                "MSGDONE" -> MessageDone(
                    sequence = line[1].takeUnless { it == "*" },
                    prog = line[2].toInt(),
                    response = decodeTlcp(line[3])
                )

                "MSGFAIL" -> MessageFail(
                    sequence = line[1].takeUnless { it == "*" },
                    prog = line[2].toInt(),
                    errorCode = line[3].toInt(),
                    errorMessage = decodeTlcp(line[4])
                )

                "NOOP" -> NoOp

                "OV" -> Overflow(
                    subscriptionId = line[1].toInt(),
                    item = line[2].toInt(),
                    overflowSize = line[3].toInt()
                )

                "PROBE" -> Probe

                "PROG" -> Progressive(progressive = line[1].toInt())

                "UNSUB" -> UnsubscriptionOk(subscriptionId = line[1].toInt())

                "REQOK" -> RequestOk(requestId = line[1])

                "REQERR" -> RequestError(requestId = line[1], code = line[2].toInt(), message = decodeTlcp(line[3]))

                "SERVNAME" -> ServerName(serverName = decodeTlcp(line[1]))

                "SYNC" -> Synchronize(secondsSinceInitialHeader = line[1].toInt())

                "SUBOK" -> SubscriptionOk(
                    subscriptionId = line[1].toInt(),
                    itemCount = line[2].toUShort(),
                    fieldCount = line[3].toUShort()
                )

                "SUBCMD" -> SubscriptionCommandOk(
                    subscriptionId = line[1].toInt(),
                    itemCount = line[2].toUShort(),
                    fieldCount = line[3].toUShort(),
                    keyFieldPos = line[4].toUShort(),
                    commandFieldPos = line[5].toUShort()
                )

                "U" -> parseU(line)

                else -> throw IllegalArgumentException("Invalid line: $line")
            }

        private fun parseU(line: List<String>): Update {
            // decode values
            val values = ArrayList<Update.Value>(line.size - 3)
            for (i in 3..<line.size) {
                val s = line[i]
                values += when {
                    // value unchanged
                    s.isEmpty() -> UNCHANGED_VALUE
                    // value empty
                    s == "\$" -> EMPTY_TEXT_VALUE
                    // value null
                    s == "#" -> NULL_TEXT_VALUE
                    // url encoded value
                    !s.startsWith('^') -> Update.Value.Text(decodeTlcp(s))
                    // skip n-1 values (last increment at the end of method)
                    s[1].isDigit() -> Update.Value.Unchanged(s.substring(1).toUShort())
                    else -> Update.Value.Patch(SupportedDiff.fromCode(s[1]), decodeTlcp(s.substring(2)))
                }
            }

            return Update(line[1].toInt(), line[2].toInt(), values)
        }

        /**
         * A modified version of [java.net.URLDecoder.decode].
         * This version does not handle the '+' translation to ' '.
         */
        private fun decodeTlcp(tlcpEncoded: String): String {
            var i = tlcpEncoded.indexOf('%')
            if (i < 0) return tlcpEncoded

            val numChars: Int = tlcpEncoded.length
            val sb = StringBuilder(if ((numChars - i) > 500) (i + numChars) / 2 else numChars)
            // add non-encoded chars
            sb.append(tlcpEncoded, 0, i)

            // (numChars-i)/3 is an upper bound for the number of remaining bytes
            val bytes = ByteArray((numChars - i) / 3)
            while (i < numChars) {
                var c = tlcpEncoded[i]
                if (c == '%') {
                    try {
                        var pos = 0
                        while (i + 2 < numChars && c == '%') {
                            val v: Int = Integer.parseInt(tlcpEncoded, i + 1, i + 3, 16)
                            require(v >= 0)
                            bytes[pos++] = v.toByte()
                            i += 3
                            if (i < numChars) c = tlcpEncoded[i]
                        }

                        require(i >= numChars || c != '%')
                        sb.append(String(bytes, 0, pos, Charsets.UTF_8))
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - ${e.message}")
                    }
                } else {
                    sb.append(c)
                    i++
                }
            }

            return sb.toString()
        }
    }
}
