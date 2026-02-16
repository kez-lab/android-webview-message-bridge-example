package io.github.kez.sample.web.message.listener.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebBridgeSerializerTest {

    @Test
    fun `extracts request id from valid json`() {
        val id = WebBridgeSerializer.extractRequestId(
            """{"id":"req-1","action":"ping","payload":{}}"""
        )

        assertEquals("req-1", id)
    }

    @Test
    fun `returns null when request id is missing or malformed`() {
        assertNull(WebBridgeSerializer.extractRequestId("{}"))
        assertNull(WebBridgeSerializer.extractRequestId("not-a-json"))
    }
}
