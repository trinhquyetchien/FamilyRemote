package com.family.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandParserTest {

    @Test
    fun testCalculateCaptureResolution() {
        val (resWidth, resHeight) = RemoteCommandParser.calculateCaptureResolution(1080, 2400, 0.5f)
        assertEquals(528, resWidth)
        assertEquals(1200, resHeight)
        assertEquals(0, resWidth % 16)
        assertEquals(0, resHeight % 16)
    }

    @Test
    fun testParseNormalizedTap() {
        val (tapX, tapY) = RemoteCommandParser.parseNormalizedTap(0.5, 0.25, 1080f, 2400f)
        assertEquals(540f, tapX, 0.001f)
        assertEquals(600f, tapY, 0.001f)
    }

    @Test
    fun testParseNormalizedTapClamping() {
        val (tapX, tapY) = RemoteCommandParser.parseNormalizedTap(-0.5, 1.5, 1080f, 2400f)
        assertEquals(0f, tapX, 0.001f)
        assertEquals(2400f, tapY, 0.001f)
    }

    @Test
    fun testGetOrientation() {
        assertEquals("portrait", RemoteCommandParser.getOrientation(1080, 2400))
        assertEquals("landscape", RemoteCommandParser.getOrientation(2400, 1080))
    }

    @Test
    fun testParseNormalizedSwipe() {
        val (start, end) = RemoteCommandParser.parseNormalizedSwipe(0.1, 0.2, 0.8, 0.9, 1000f, 2000f)
        assertEquals(100f, start.first, 0.001f)
        assertEquals(400f, start.second, 0.001f)
        assertEquals(800f, end.first, 0.001f)
        assertEquals(1800f, end.second, 0.001f)
    }

    @Test
    fun testConvertToWebSocketUrl() {
        assertEquals(
            "wss://fast.trycloudflare.com/ws",
            RemoteCommandParser.convertToWebSocketUrl("https://fast.trycloudflare.com")
        )
        assertEquals(
            "wss://fast.trycloudflare.com/ws",
            RemoteCommandParser.convertToWebSocketUrl("https://fast.trycloudflare.com/")
        )
        assertEquals(
            "wss://fast.trycloudflare.com/ws",
            RemoteCommandParser.convertToWebSocketUrl("wss://fast.trycloudflare.com/ws")
        )
        assertEquals(
            "ws://192.168.1.100:8080",
            RemoteCommandParser.convertToWebSocketUrl("http://192.168.1.100:8080")
        )
    }
}
