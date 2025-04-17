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

import com.lightstreamer.kotlin.socket.*
import com.lightstreamer.kotlin.socket.diff.SupportedDiff
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Message sent from client to Lightstreamer server
 */
public sealed class LightstreamerClientMessage(public val name: LightstreamerClientRequestName) {

    public abstract val id: String

    public final override fun toString(): String =
        parameters().asSequence().joinToString(prefix = "$name(", postfix = ")") { (key, value) -> "$key=$value" }

    /**
     * List of already URL encoded parameters
     */
    internal abstract fun parameters(): Iterator<Parameter>

    public data class BindSession(
        val session: String,
        val recoveryFrom: Int? = null,
        val keepAlive: Duration? = null,
        val inactivity: Duration? = null,
        val sendSync: Boolean = true
    ) : LightstreamerClientMessage(LightstreamerClientRequestName.BIND_SESSION) {

        public override val id: String get() = LightstreamerClientRequestName.BIND_SESSION.name

        override fun parameters(): Iterator<Parameter> = iterator {
            yieldParameter("LS_session", session, false)
            yieldParameter("LS_recovery_from", recoveryFrom)
            yieldParameter("LS_keepalive_millis", keepAlive?.inWholeMilliseconds)
            yieldParameter("LS_inactivity_millis", inactivity?.inWholeMilliseconds)
            yieldParameter("LS_send_sync", sendSync.takeUnless { it })
        }
    }

    public data class CreateSession(
        val adapterSetName: String,
        val userCredential: UsernamePassword? = null,
        val keepAlive: Duration? = null,
        val inactivity: Duration? = null,
        val polling: Duration? = null,
        val supportedDiffs: Set<SupportedDiff> = emptySet(),
        val reduceHead: Boolean = false,
        val ttl: TTL = TTL.UNKNOWN,
        val sendSync: Boolean = true
    ) : LightstreamerClientMessage(LightstreamerClientRequestName.CREATE_SESSION) {

        public override val id: String get() = LightstreamerClientRequestName.CREATE_SESSION.name

        override fun parameters(): Iterator<Parameter> = iterator {
            yieldParameter("LS_adapter_set", adapterSetName.takeUnless { it == "DEFAULT" }, true)
            yieldParameter("LS_cid", LightstreamerTlcpSocket.clientCidCode, true)
            yieldParameter("LS_user", userCredential?.username, true)
            yieldParameter("LS_password", userCredential?.password?.takeIf { it.isNotEmpty() }, true)
            yieldParameter("LS_keepalive_millis", keepAlive?.takeIf { polling == null }?.inWholeMilliseconds)
            yieldParameter("LS_inactivity_millis", inactivity?.takeIf { polling == null }?.inWholeMilliseconds)
            yieldParameter("LS_polling", (polling != null).takeIf { it })
            yieldParameter("LS_polling_millis", polling?.inWholeMilliseconds)
            yieldParameter("LS_supported_diffs", supportedDiffs.joinToString { it.code.toString() }, false)
            yieldParameter("LS_reduce_head", reduceHead.takeIf { it })
            yieldParameter("LS_ttl_millis", ttl.takeIf { it != TTL.UNKNOWN }?.toString(), false)
            yieldParameter("LS_send_sync", sendSync.takeIf { !it && polling == null })
        }

        public sealed class TTL {
            public data class Millis(val millis: Int) : TTL() {
                override fun toString(): String = millis.toString()
            }

            public object UNKNOWN : TTL() {
                override fun toString(): String = "unknown"
            }

            public object UNLIMITED : TTL() {
                override fun toString(): String = "unlimited"
            }
        }
    }

    public data class Destroy(
        val causeCode: Int? = null,
        val causeMessage: String? = null,
        val closeSocket: Boolean = false
    ) : LightstreamerClientMessage(LightstreamerClientRequestName.CONTROL) {

        public override val id: String get() = "destroy"

        override fun parameters(): Iterator<Parameter> = iterator {
            yieldParameter("LS_op", "destroy", false)
            yieldParameter("LS_reqId", id, false)
            yieldParameter("LS_cause_code", causeCode)
            yieldParameter("LS_cause_message", causeMessage, true)
            yieldParameter("LS_close_socket", closeSocket.takeIf { it })
        }
    }

    public object Heartbeat : LightstreamerClientMessage(LightstreamerClientRequestName.HEARTBEAT) {

        public override val id: String get() = LightstreamerClientRequestName.HEARTBEAT.name

        override fun parameters() = emptyList<Parameter>().iterator()
    }

    public data class Message(
        val message: String,
        val sequence: String? = null,
        val ack: Boolean = true,
        val outcome: Boolean = true,
        val msgProg: Int? = null
    ) : LightstreamerClientMessage(LightstreamerClientRequestName.MESSAGE) {

        init {
            require(sequence != "*") { "Invalid sequence name: $sequence" }
        }

        public override val id: String =
            if (sequence == null && msgProg != null) "m$msgProg"
            else "M${idGenerator.incrementAndGet()}"

        override fun parameters(): Iterator<Parameter> = iterator {
            yieldParameter("LS_reqId", id, false)
            yieldParameter("LS_message", message, true)
            yieldParameter("LS_sequence", sequence, true)
            yieldParameter("LS_ack", ack.takeUnless { it })
            yieldParameter("LS_outcome", outcome.takeUnless { it })
            yieldParameter("LS_msg_prog", msgProg)
        }
    }

    public data class Subscribe(
        val subscriptionId: Int,
        val dataAdapterName: String,
        val mode: SubscriptionMode,
        val itemGroup: String,
        val itemSchema: List<String>,
        val requestSnapshot: Boolean,
        val ack: Boolean = true
    ) : LightstreamerClientMessage(LightstreamerClientRequestName.CONTROL) {

        public override val id: String = requestIdForSubscription(subscriptionId)

        override fun parameters(): Iterator<Parameter> = iterator {
            yieldParameter("LS_reqId", id, false)
            yieldParameter("LS_subId", subscriptionId)
            yieldParameter("LS_op", "add", false)
            yieldParameter("LS_data_adapter", dataAdapterName, true)
            yieldParameter("LS_mode", mode.name, false)
            yieldParameter("LS_group", itemGroup, true)
            yieldParameter("LS_schema", itemSchema.joinToString(separator = " "), true)
            yieldParameter("LS_snapshot", requestSnapshot)
            yieldParameter("LS_ack", ack.takeUnless { ack })
        }

        public companion object {
            public fun requestIdForSubscription(subscriptionId: Int): String = "s$subscriptionId"
        }
    }

    public data class Unsubscribe(val subscriptionId: Int) :
        LightstreamerClientMessage(LightstreamerClientRequestName.CONTROL) {

        public override val id: String = requestIdForSubscription(subscriptionId)

        override fun parameters(): Iterator<Parameter> = iterator {
            yieldParameter("LS_reqId", id, false)
            yieldParameter("LS_subId", subscriptionId)
            yieldParameter("LS_op", "delete", false)
        }

        public companion object {
            public fun requestIdForSubscription(subscriptionId: Int): String = "u$subscriptionId"
        }
    }

    public data class Parameter(val key: String, val value: CharSequence, val requireEncoding: Boolean)

    private companion object {
        private val idGenerator = AtomicInteger()
    }
}

private suspend inline fun SequenceScope<LightstreamerClientMessage.Parameter>.yieldParameter(
    key: String, value: Boolean?
) {
    yieldParameter(key, value?.toString(), false)
}

private suspend inline fun SequenceScope<LightstreamerClientMessage.Parameter>.yieldParameter(
    key: String, value: Int?
) {
    yieldParameter(key, value?.toString(), false)
}

private suspend inline fun SequenceScope<LightstreamerClientMessage.Parameter>.yieldParameter(
    key: String, value: Long?
) {
    yieldParameter(key, value?.toString(), false)
}

private suspend inline fun SequenceScope<LightstreamerClientMessage.Parameter>.yieldParameter(
    key: String, value: CharSequence?, requireEncoding: Boolean
) {
    if (value != null) yield(LightstreamerClientMessage.Parameter(key, value, requireEncoding))
}
