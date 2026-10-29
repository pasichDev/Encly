package com.pasich.encly.editor.ui.blocks

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [toAsciiHost] on a fixed set of hosts, with the answers `java.net.IDN.toASCII` gave on the JVM. The test runs on
 * the JVM targets against the real IDN (which proves the table) and on iOS against Nameprep and Punycode (which
 * have to agree with it). The hosts arrive lower-cased, as `parseNoteLink` passes them.
 *
 * Left out on purpose: names with an emoji or another code point Unicode 3.2 had not assigned (the JDK rejects
 * them, iOS accepts them, as browsers do), and the bidirectional rules of RFC 3454 (iOS does not check them).
 */
class AsciiHostTest {
    private val answers = mapOf(
        "bücher.de" to "xn--bcher-kva.de",
        "münchen.example" to "xn--mnchen-3ya.example",
        "аpple.com" to "xn--pple-43d.com",
        "пример.рф" to "xn--e1afmkfd.xn--p1ai",
        "www.bücher.de" to "www.xn--bcher-kva.de",
        "ñandú.es" to "xn--and-6ma2c.es",
        "日本語.jp" to "xn--wgv71a119e.jp",
        "例え.テスト" to "xn--r8jz45g.xn--zckzah",
        "bücher.de." to "xn--bcher-kva.de.",
        // Normalization (NFKC): fullwidth letters, ligatures, superscripts, circled digits, composed accents.
        "ｅｘａｍｐｌｅ.com" to "example.com",
        "ｂücher.de" to "xn--bcher-kva.de",
        "ｗｗｗ.example.com" to "www.example.com",
        "ﬁsh.com" to "fish.com",
        "x².com" to "x2.com",
        "①.com" to "1.com",
        "é.com" to "xn--9ca.com",
        "å.se" to "xn--5ca.se",
        "ａ́.com" to "xn--1ca.com",
        // Case folding beyond lower-casing.
        "straße.de" to "strasse.de",
        "fußball.de" to "fussball.de",
        "ς.gr" to "xn--4xa.gr",
        "σασ.gr" to "xn--mxa9ab.gr",
        "ǰ.com" to "xn--ska.com",
        "ẖ.com" to "xn--ckg.com",
        "i̇stanbul.tr" to "xn--istanbul-o0e.tr",
        // Characters that map to nothing.
        "ex­ample.de" to "example.de",
        "a‍b.com" to "ab.com",
        "a‌b.com" to "ab.com",
        "a﻿b.com" to "ab.com",
        "ö​ö.de" to "xn--ndaa.de",
        // The other IDNA dots separate labels.
        "bücher。de" to "xn--bcher-kva.de",
        "bücher．de" to "xn--bcher-kva.de",
        "bücher｡de" to "xn--bcher-kva.de",
        // ASCII is left as it is, an encoded label too.
        "a_b.com" to "a_b.com",
        "xn--bcher-kva.de" to "xn--bcher-kva.de",
    )

    private val invalid = listOf(
        // Prohibited: private use.
        ".com",
        // Starts with the ACE prefix after preparation.
        "xn--ü.com",
        // Empty labels.
        "a..bü",
        ".bü",
        "bü..de",
        // A label over 63 characters once encoded, and one over it as typed.
        "ж".repeat(60) + ".com",
        "ü" + "a".repeat(70) + ".com",
    )

    @Test
    fun theHostsGetTheAnswersOfJavaNetIdn() {
        answers.forEach { (host, ascii) -> assertEquals(ascii, toAsciiHost(host), host) }
    }

    @Test
    fun theInvalidNamesAreRefused() {
        invalid.forEach { host -> assertEquals(null, toAsciiHost(host), host) }
    }
}
