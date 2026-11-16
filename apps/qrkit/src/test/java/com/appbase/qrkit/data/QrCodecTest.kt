package com.appbase.qrkit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodecTest {

    /** 把矩阵放大成像素，模拟一张图片；[invert] 为 true 时是深底浅码 */
    private fun pixels(text: String, scale: Int = 4, invert: Boolean = false): Triple<Int, Int, IntArray> {
        val m = QrCodec.encode(text)
        val side = m.width * scale
        val dark = if (invert) WHITE else BLACK
        val light = if (invert) BLACK else WHITE
        val out = IntArray(side * side) { i -> if (m.get((i % side) / scale, (i / side) / scale)) dark else light }
        return Triple(side, side, out)
    }

    @Test
    fun roundTripsAllContentTypes() {
        listOf(
            QrContent.Link("example.com/path?q=1").encode(),
            QrContent.Text("Hello, 世界 ✓").encode(),
            QrContent.Wifi("Cafe; Guest", "p@ss:word", WifiSecurity.WPA).encode(),
            QrContent.Contact("Ann Lee", "+1 555 0100", "ann@example.com").encode(),
        ).forEach { text ->
            val (w, h, px) = pixels(text)
            assertEquals(text, QrCodec.decode(w, h, px))
        }
    }

    @Test
    fun readsInvertedCodes() {
        val (w, h, px) = pixels("https://example.com", invert = true)
        assertEquals("https://example.com", QrCodec.decode(w, h, px))
    }

    @Test
    fun returnsNullWithoutCode() {
        val blank = IntArray(200 * 200) { WHITE }
        assertNull(QrCodec.decode(200, 200, blank))
    }

    @Test
    fun longestAllowedTextStillEncodes() {
        val text = "a".repeat(QrContent.MAX_LENGTH)
        val (w, h, px) = pixels(text, scale = 3)
        assertEquals(text, QrCodec.decode(w, h, px))
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
