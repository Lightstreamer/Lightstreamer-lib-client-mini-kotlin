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
package com.lightstreamer.kotlin.socket

import com.lightstreamer.kotlin.socket.diff.SupportedDiff
import com.lightstreamer.kotlin.socket.internal.*
import com.lightstreamer.kotlin.socket.message.*
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

class TlcpParserTest {
    @Test
    fun `parse unchanged U`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("U,10,20,\r\n").toList()
        val update = serverMessages.single() as LightstreamerServerMessage.Update
        update.subscriptionId `should be equal to` 10
        update.itemId `should be equal to` 20
        update.values `should be equal to` listOf(LightstreamerServerMessage.Update.Value.Unchanged(1u))
    }

    @Test
    fun `parse unchanged2 U`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("U,10,20,^2\r\n").toList()
        val update = serverMessages.single() as LightstreamerServerMessage.Update
        update.subscriptionId `should be equal to` 10
        update.itemId `should be equal to` 20
        update.values `should be equal to` listOf(LightstreamerServerMessage.Update.Value.Unchanged(2u))
    }

    @Test
    fun `parse null U`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("U,10,20,#\r\n").toList()
        val update = serverMessages.single() as LightstreamerServerMessage.Update
        update.subscriptionId `should be equal to` 10
        update.itemId `should be equal to` 20
        update.values `should be equal to` listOfValues(null)
    }

    @Test
    fun `parse simple U`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("U,10,20,a\r\n").toList()
        val update = serverMessages.single() as LightstreamerServerMessage.Update
        update.subscriptionId `should be equal to` 10
        update.itemId `should be equal to` 20
        update.values `should be equal to` listOfValues("a")
    }

    @Test
    fun `parse special U`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("U,10,20,a,b|+c%25\r\n").toList()
        val update = serverMessages.single() as LightstreamerServerMessage.Update
        update.subscriptionId `should be equal to` 10
        update.itemId `should be equal to` 20
        update.values `should be equal to` listOfValues("a,b", "+c%")
    }

    @Test
    fun `parse TLCP-diff U`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("U,10,20,^Tabc%25\r\n").toList()
        val update = serverMessages.single() as LightstreamerServerMessage.Update
        update.subscriptionId `should be equal to` 10
        update.itemId `should be equal to` 20
        update.values `should be equal to`
                listOf(LightstreamerServerMessage.Update.Value.Patch(SupportedDiff.TLCPDiff, "abc%"))
    }

    @Test
    fun `parse error`() {
        val tlcpParser = TlcpParser()
        val serverMessages = tlcpParser.parse("ERROR,1,+3%25\r\n").toList()
        val error = serverMessages.single() as LightstreamerServerMessage.Error
        error.code `should be equal to` 1
        error.message `should be equal to` "+3%"
    }

    private fun listOfValues(vararg values: String?) =
        listOf(*values).map { LightstreamerServerMessage.Update.Value.Text(it) }
}
