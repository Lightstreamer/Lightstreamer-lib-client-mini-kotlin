package com.lightstreamer.client.socket.diff

public enum class SupportedDiff(public val code: Char) {
    JSONPatch('P'), TLCPDiff('T');

    public companion object {
        public fun fromCode(code: Char): SupportedDiff =
            when (code) {
                'P' -> JSONPatch
                'T' -> TLCPDiff
                else -> throw IllegalArgumentException("Diff not found: '$code'")
            }
    }
}
