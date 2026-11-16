package com.appbase.qrkit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrContentTest {

    @Test
    fun linkGetsHttpsWhenSchemeIsMissing() {
        assertEquals("https://example.com/a", QrContent.Link("example.com/a").encode())
        assertEquals("http://example.com", QrContent.Link("http://example.com").encode())
        assertEquals("example.com/a", QrContent.Link("https://example.com/a/").title())
    }

    @Test
    fun linkValidation() {
        assertNull(QrContent.Link("example.com").problem())
        assertNotNull(QrContent.Link("not a link").problem())
        assertNotNull(QrContent.Link("localhost").problem())
        assertNotNull(QrContent.Link("").problem())
    }

    @Test
    fun wifiEscapesSpecialCharacters() {
        val wifi = QrContent.Wifi("My;Net", "p:a\\ss,\"x\"", WifiSecurity.WPA)
        assertEquals("WIFI:T:WPA;S:My\\;Net;P:p\\:a\\\\ss\\,\\\"x\\\";;", wifi.encode())
    }

    @Test
    fun wifiRoundTrip() {
        val original = QrContent.Wifi("Cafe; Guest", "se:cr\\et", WifiSecurity.WPA, hidden = true)
        assertEquals(original, QrContent.parse(original.encode()))
        val open = QrContent.Wifi("Lobby", "", WifiSecurity.NONE)
        assertEquals("WIFI:T:nopass;S:Lobby;;", open.encode())
        assertEquals(open, QrContent.parse(open.encode()))
    }

    @Test
    fun wifiNeedsPasswordUnlessOpen() {
        assertNotNull(QrContent.Wifi("Home", "", WifiSecurity.WPA).problem())
        assertNull(QrContent.Wifi("Home", "", WifiSecurity.NONE).problem())
        assertNotNull(QrContent.Wifi(" ", "x", WifiSecurity.WPA).problem())
    }

    @Test
    fun contactRoundTripThroughVcard() {
        val original = QrContent.Contact("Ann Lee, Jr.", "+1 555 0100", "ann@example.com", "Acme; Inc")
        val vcard = original.encode()
        assertTrue(vcard.startsWith("BEGIN:VCARD\nVERSION:3.0\n"))
        assertTrue(vcard.endsWith("END:VCARD"))
        assertEquals(original, QrContent.parse(vcard))
    }

    @Test
    fun parsesMecard() {
        val parsed = QrContent.parse("MECARD:N:Lee,Ann;TEL:5550100;EMAIL:ann@example.com;;")
        assertEquals(QrContent.Contact("Ann Lee", "5550100", "ann@example.com", ""), parsed)
    }

    @Test
    fun parsesSimpleSchemes() {
        assertEquals(QrContent.Email("a@b.co"), QrContent.parse("mailto:a@b.co?subject=hi"))
        assertEquals(QrContent.Phone("+15550100"), QrContent.parse("tel:+15550100"))
        assertEquals(QrContent.Link("https://example.com/x?y=1"), QrContent.parse("https://example.com/x?y=1"))
        assertEquals(QrContent.Text("hello world"), QrContent.parse("hello world"))
        assertEquals(QrContent.Text("https://a.com and more"), QrContent.parse("https://a.com and more"))
    }

    @Test
    fun textLimitAndTitle() {
        assertNotNull(QrContent.Text("").problem())
        assertNotNull(QrContent.Text("x".repeat(QrContent.MAX_LENGTH + 1)).problem())
        assertNull(QrContent.Text("x".repeat(QrContent.MAX_LENGTH)).problem())
        assertEquals("second", QrContent.Text("\n  second\nthird").title())
    }

    @Test
    fun emailAndPhoneValidation() {
        assertNull(QrContent.Email("a@b.co").problem())
        assertNotNull(QrContent.Email("a@b").problem())
        assertNull(QrContent.Phone("+1 (555) 0100").problem())
        assertNotNull(QrContent.Phone("call me").problem())
    }
}
