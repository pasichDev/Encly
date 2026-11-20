package com.pasich.encly.editor.ui.blocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure-Kotlin encoder iOS uses, and the platform's `toAsciiHost` it has to agree with. */
class PunycodeTest {
    private val hosts = mapOf(
        // Latin with diacritics: basic characters stay in front.
        "bücher.de" to "xn--bcher-kva.de",
        "münchen.example" to "xn--mnchen-3ya.example",
        // The homograph the link card guards against: a Cyrillic "а" in an otherwise Latin name.
        "аpple.com" to "xn--pple-43d.com",
        // A whole label in another script, two labels.
        "пример.рф" to "xn--e1afmkfd.xn--p1ai",
        // Only the label with a non-ASCII character changes.
        "www.bücher.de" to "www.xn--bcher-kva.de",
        // Several non-ASCII characters, repeated and out of order.
        "ñandú.es" to "xn--and-6ma2c.es",
        "\u65E5\u672C\u8A9E.jp" to "xn--wgv71a119e.jp",
    )

    // Checked against Python's independent punycode codec; the platform's IDN does not take all of them.
    private val encoderOnly = mapOf(
        // Far-apart scripts: the bias adapts over a large gap.
        "\u044F\u65E5\u672C.com" to "xn--41a8701cb2a.com",
        "\u00E9\u65E5" to "xn--9ca8182c",
        // A code point outside the basic plane between two ASCII letters.
        "a\uD83D\uDCA9b.la" to "xn--ab-8t72a.la",
    )

    @Test
    fun encodesNamesWithFarApartScripts() {
        encoderOnly.forEach { (host, ascii) -> assertEquals(ascii, Punycode.toAsciiHost(host), host) }
    }

    @Test
    fun encodesTheReferenceNames() {
        hosts.forEach { (host, ascii) -> assertEquals(ascii, Punycode.toAsciiHost(host), host) }
    }

    @Test
    fun aSurrogatePairIsOneCodePoint() {
        assertEquals("xn--ls8h.la", Punycode.toAsciiHost("\uD83D\uDCA9.la"))
    }

    @Test
    fun thePlatformAgreesWithTheEncoder() {
        hosts.forEach { (host, ascii) -> assertEquals(ascii, toAsciiHost(host), host) }
    }

    @Test
    fun anAsciiNameIsKept() {
        assertEquals("example.com", Punycode.toAsciiHost("example.com"))
    }

    @Test
    fun aTrailingRootDotIsKept() {
        assertEquals("xn--bcher-kva.de.", Punycode.toAsciiHost("bücher.de."))
    }

    @Test
    fun anEmptyLabelMakesTheNameInvalid() {
        assertNull(Punycode.toAsciiHost("a..b"))
        assertNull(Punycode.toAsciiHost(".example.com"))
        assertNull(Punycode.toAsciiHost(""))
        assertNull(toAsciiHost("a..bü"))
    }

    @Test
    fun aLabelLongerThanDnsAllowsMakesTheNameInvalid() {
        assertNull(Punycode.toAsciiHost("ü" + "a".repeat(70) + ".com"))
        // Short as typed, too long once encoded.
        assertNull(Punycode.toAsciiHost("ж".repeat(60) + ".com"))
    }

    @Test
    fun aLoneSurrogateIsEncodedAsItIs() {
        assertTrue(Punycode.toAsciiHost("\uD83D")!!.startsWith("xn--"))
        assertTrue(Punycode.toAsciiHost("\uDCA9")!!.startsWith("xn--"))
    }
}
