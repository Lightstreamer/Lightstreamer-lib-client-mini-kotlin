package com.lightstreamer.kotlin.socket

import com.lightstreamer.kotlin.socket.diff.SupportedDiff
import com.lightstreamer.kotlin.socket.diff.TlcpDiffDecoder
import com.lightstreamer.kotlin.socket.message.*
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Test

class TlcpDiffTest {

    @Test
    fun unchanged() {
        check("", "", "")
        check("", "aaa", "")
        check("1", "b", "1")
        check("123", "d", "123")
        check("123", "aaad", "123")
        check("123", "daaa", "123")
        check("1234567890123456789012345678901234567890", "Bo", "1234567890123456789012345678901234567890")
    }

    @Test
    fun updateStart() {
        check("123456789", "adabcdg", "abc456789")
        check("1" + ".".repeat(999), "ab2bBMl", "2" + ".".repeat(999))
    }

    @Test
    fun updateMiddle() {
        check("123456789", "ddabcdd", "123abc789")
        check("1" + ".".repeat(999), "aabBMlb2", ".".repeat(999) + "2")
        check(".".repeat(999) + "1", "ab2aBMl", "2" + ".".repeat(999))
        check(".".repeat(999) + "1", "ab2aBMl", "2" + ".".repeat(999))
        check("1" + ".".repeat(999) + "2", "ab3bBMlb4", "3" + ".".repeat(999) + "4")
    }

    @Test
    fun updateEnd() {
        check("123456789", "gdabc", "123456abc")
        check(".".repeat(999) + "1", "BMlb2", ".".repeat(999) + "2")
    }

    private fun check(old: String, patch: String, new: String) {
        assertEquals(
            new,
            TlcpDiffDecoder.patch(old, LightstreamerServerMessage.Update.Value.Patch(SupportedDiff.TLCPDiff, patch))
        )
    }
}
