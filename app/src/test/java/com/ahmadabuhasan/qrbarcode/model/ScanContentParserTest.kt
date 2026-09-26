package com.ahmadabuhasan.qrbarcode.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric is needed because the parser uses android.net.Uri for sms:, mailto: and geo:.
@RunWith(RobolectricTestRunner::class)
class ScanContentParserTest {

    private inline fun <reified T : ScanContent> parseAs(text: String): T {
        val result = ScanContentParser.parse(text)
        assertTrue("Expected ${T::class.simpleName} but was $result", result is T)
        return result as T
    }

    // --- URL ---

    @Test
    fun `http and https are urls regardless of case`() {
        parseAs<ScanContent.Url>("https://example.com/path?q=1")
        parseAs<ScanContent.Url>("http://example.com")
        parseAs<ScanContent.Url>("HTTPS://EXAMPLE.COM")
    }

    @Test
    fun `bare domain without scheme is text`() {
        parseAs<ScanContent.Text>("example.com")
    }

    // --- Wi-Fi ---

    @Test
    fun `wifi fields are parsed`() {
        val wifi = parseAs<ScanContent.Wifi>("WIFI:S:MyNetwork;T:WPA;P:secret123;H:true;;")
        assertEquals("MyNetwork", wifi.ssid)
        assertEquals("secret123", wifi.password)
        assertEquals("WPA", wifi.security)
        assertTrue(wifi.hidden)
    }

    @Test
    fun `wifi field order does not matter`() {
        val wifi = parseAs<ScanContent.Wifi>("WIFI:T:WEP;P:pass;S:Cafe;;")
        assertEquals("Cafe", wifi.ssid)
        assertEquals("pass", wifi.password)
        assertEquals("WEP", wifi.security)
        assertFalse(wifi.hidden)
    }

    @Test
    fun `wifi unescapes special characters`() {
        val wifi = parseAs<ScanContent.Wifi>("""WIFI:S:Cafe\;Lt\:2;T:WPA;P:a\\b\,c\"d;;""")
        assertEquals("Cafe;Lt:2", wifi.ssid)
        assertEquals("""a\b,c"d""", wifi.password)
    }

    @Test
    fun `wifi open network has empty password`() {
        val wifi = parseAs<ScanContent.Wifi>("WIFI:S:Guest;T:nopass;;")
        assertEquals("", wifi.password)
        assertEquals("nopass", wifi.security)
    }

    @Test
    fun `wifi keys and prefix are case insensitive`() {
        val wifi = parseAs<ScanContent.Wifi>("wifi:s:Home;p:pw;;")
        assertEquals("Home", wifi.ssid)
        assertEquals("pw", wifi.password)
    }

    @Test
    fun `wifi without ssid falls back to text`() {
        parseAs<ScanContent.Text>("WIFI:T:WPA;P:secret;;")
    }

    // --- Phone ---

    @Test
    fun `tel is parsed`() {
        assertEquals("+6281234567890", parseAs<ScanContent.Phone>("tel:+6281234567890").number)
        assertEquals("112", parseAs<ScanContent.Phone>("TEL:112").number)
    }

    @Test
    fun `empty tel falls back to text`() {
        parseAs<ScanContent.Text>("tel:")
    }

    // --- SMS ---

    @Test
    fun `smsto with body is parsed`() {
        val sms = parseAs<ScanContent.Sms>("SMSTO:+62812:Hello: world")
        assertEquals("+62812", sms.number)
        assertEquals("Hello: world", sms.body)
    }

    @Test
    fun `smsto without body is parsed`() {
        val sms = parseAs<ScanContent.Sms>("smsto:12345")
        assertEquals("12345", sms.number)
        assertEquals("", sms.body)
    }

    @Test
    fun `sms uri with body is parsed`() {
        val sms = parseAs<ScanContent.Sms>("sms:+62812?body=Hi%20there")
        assertEquals("+62812", sms.number)
        assertEquals("Hi there", sms.body)
    }

    @Test
    fun `sms uri without body is parsed`() {
        val sms = parseAs<ScanContent.Sms>("sms:12345")
        assertEquals("12345", sms.number)
        assertEquals("", sms.body)
    }

    @Test
    fun `empty smsto falls back to text`() {
        parseAs<ScanContent.Text>("SMSTO::body only")
    }

    // --- Email ---

    @Test
    fun `mailto with subject and body is parsed`() {
        val email = parseAs<ScanContent.Email>("mailto:a@b.com?subject=Hi%20there&body=Line")
        assertEquals("a@b.com", email.to)
        assertEquals("Hi there", email.subject)
        assertEquals("Line", email.body)
    }

    @Test
    fun `mailto without query is parsed`() {
        val email = parseAs<ScanContent.Email>("mailto:a@b.com")
        assertEquals("a@b.com", email.to)
        assertEquals("", email.subject)
        assertEquals("", email.body)
    }

    @Test
    fun `matmsg is parsed`() {
        val email = parseAs<ScanContent.Email>("MATMSG:TO:a@b.com;SUB:Hello;BODY:See you\\; bye;;")
        assertEquals("a@b.com", email.to)
        assertEquals("Hello", email.subject)
        assertEquals("See you; bye", email.body)
    }

    @Test
    fun `matmsg without recipient falls back to text`() {
        parseAs<ScanContent.Text>("MATMSG:SUB:Hello;;")
    }

    // --- Geo ---

    @Test
    fun `geo coordinates are parsed`() {
        val geo = parseAs<ScanContent.Geo>("geo:-6.1754,106.8272")
        assertEquals("-6.1754", geo.lat)
        assertEquals("106.8272", geo.lng)
        assertNull(geo.query)
    }

    @Test
    fun `geo query is parsed`() {
        val geo = parseAs<ScanContent.Geo>("geo:-6.1754,106.8272?q=Monas%20Jakarta")
        assertEquals("-6.1754", geo.lat)
        assertEquals("106.8272", geo.lng)
        assertEquals("Monas Jakarta", geo.query)
    }

    @Test
    fun `geo altitude is not part of longitude`() {
        val geo = parseAs<ScanContent.Geo>("geo:-6.1754,106.8272,50")
        assertEquals("106.8272", geo.lng)
    }

    @Test
    fun `geo without longitude falls back to text`() {
        parseAs<ScanContent.Text>("geo:-6.1754")
    }

    // --- vCard ---

    @Test
    fun `vcard fields are parsed`() {
        val vcard = parseAs<ScanContent.VCard>(
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:John Doe
            TEL;TYPE=CELL:+62812
            EMAIL;TYPE=INTERNET:john@example.com
            END:VCARD
            """.trimIndent()
        )
        assertEquals("John Doe", vcard.name)
        assertEquals("+62812", vcard.phone)
        assertEquals("john@example.com", vcard.email)
    }

    @Test
    fun `vcard prefers FN over N even when N comes first`() {
        val vcard = parseAs<ScanContent.VCard>(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Doe;John;;;\r\nFN:John Doe\r\nEND:VCARD"
        )
        assertEquals("John Doe", vcard.name)
    }

    @Test
    fun `vcard falls back to N as given then family name`() {
        val vcard = parseAs<ScanContent.VCard>("BEGIN:VCARD\nN:Doe;John;;;\nEND:VCARD")
        assertEquals("John Doe", vcard.name)
    }

    @Test
    fun `vcard NOTE is not mistaken for N`() {
        val vcard = parseAs<ScanContent.VCard>("BEGIN:VCARD\nNOTE:hello\nFN:Jane\nEND:VCARD")
        assertEquals("Jane", vcard.name)
    }

    @Test
    fun `vcard with missing fields has nulls`() {
        val vcard = parseAs<ScanContent.VCard>("BEGIN:VCARD\nFN:Only Name\nEND:VCARD")
        assertEquals("Only Name", vcard.name)
        assertNull(vcard.phone)
        assertNull(vcard.email)
    }

    // --- Calendar ---

    @Test
    fun `vevent fields are parsed`() {
        val event = parseAs<ScanContent.CalendarEvent>(
            """
            BEGIN:VEVENT
            SUMMARY:Team meeting
            LOCATION:Room 1
            DESCRIPTION:Weekly sync
            DTSTART:20260901T090000Z
            DTEND:20260901T100000Z
            END:VEVENT
            """.trimIndent()
        )
        assertEquals("Team meeting", event.title)
        assertEquals("Room 1", event.location)
        assertEquals("Weekly sync", event.description)
        assertEquals("20260901T090000Z", event.start)
        assertEquals("20260901T100000Z", event.end)
    }

    @Test
    fun `vcalendar wrapping a vevent is parsed with parameters stripped`() {
        val event = parseAs<ScanContent.CalendarEvent>(
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nSUMMARY:Launch\r\n" +
                "DTSTART;VALUE=DATE:20261001\r\nEND:VEVENT\r\nEND:VCALENDAR"
        )
        assertEquals("Launch", event.title)
        assertEquals("20261001", event.start)
        assertNull(event.end)
    }

    @Test
    fun `vcalendar without vevent is text`() {
        parseAs<ScanContent.Text>("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR")
    }

    // --- Text ---

    @Test
    fun `unknown payloads are text and keep raw`() {
        val text = parseAs<ScanContent.Text>("8991002101234")
        assertEquals("8991002101234", text.raw)
        parseAs<ScanContent.Text>("")
        parseAs<ScanContent.Text>("bitcoin:1BoatSLRHtKNngkdXEeobR76b53LETtpyT")
    }
}
