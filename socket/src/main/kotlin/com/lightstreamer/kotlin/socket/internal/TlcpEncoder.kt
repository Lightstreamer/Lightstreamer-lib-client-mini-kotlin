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
package com.lightstreamer.kotlin.socket.internal

import com.lightstreamer.kotlin.socket.message.*

/**
 * Encode the message to [Appendable]
 */
internal fun Appendable.appendTlcpEncoded(message: LightstreamerClientMessage) {
    val name = when (message.name) {
        LightstreamerClientRequestName.BIND_SESSION -> "bind_session"
        LightstreamerClientRequestName.CONTROL -> "control"
        LightstreamerClientRequestName.CREATE_SESSION -> "create_session"
        LightstreamerClientRequestName.HEARTBEAT -> "heartbeat"
        LightstreamerClientRequestName.MESSAGE -> "msg"
    }
    append(name)
    append('\r')
    append('\n')
    appendTlcpEncodedParameters(message)
}

/**
 * Encode only the message's parameter to [Appendable]
 */
internal fun Appendable.appendTlcpEncodedParameters(message: LightstreamerClientMessage) {
    var firstParameter = true
    for ((key, value, requireEncoding) in message.parameters()) {
        if (firstParameter) firstParameter = false
        else append('&')
        append(key)
        append('=')
        if (requireEncoding) appendTlcpEncoded(value)
        else append(value)
    }
    append('\r')
    append('\n')
}

internal fun Appendable.appendTlcpEncoded(value: CharSequence) {
    for (char in value) {
        when (char) {
            // line delimiters
            '\r' -> append("%0D")
            '\n' -> append("%0A")
            // used for percent-encoding
            '%' -> append("%25")
            '+' -> append("%2B")
            // parameter delimiters
            '&' -> append("%26")
            '=' -> append("%3D")
            // otherwise encoding is not required,
            // all non-ascii characters included
            else -> append(char)
        }
    }
}
