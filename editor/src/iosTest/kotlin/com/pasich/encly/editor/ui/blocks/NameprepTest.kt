package com.pasich.encly.editor.ui.blocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Nameprep steps of the iOS actual (NFKC through Foundation), which the JVM tests never run. */
class NameprepTest {
    @Test
    fun anAsciiNameIsLeftAsItIs() {
        assertEquals("Example.COM", Nameprep.prepare("Example.COM"))
    }

    @Test
    fun theCaseIsFoldedAndSharpSBecomesSs() {
        assertEquals("m\u00FCnchen.de", Nameprep.prepare("M\u00DCNCHEN.de"))
        assertEquals("strasse.de", Nameprep.prepare("stra\u00DFe.de"))
        assertEquals("\u03C3\u03C3.de", Nameprep.prepare("\u03A3\u03A3.de"))
        assertEquals("\u03C3.de", Nameprep.prepare("\u03C2.de"))
    }

    @Test
    fun compatibilityCharactersAreNormalizedToNfkc() {
        // The "fi" ligature, a fullwidth letter and an accent written as a separate combining mark.
        assertEquals("fi.de", Nameprep.prepare("\uFB01.de"))
        assertEquals("a.de", Nameprep.prepare("\uFF21.de"))
        assertEquals("caf\u00E9.de", Nameprep.prepare("cafe\u0301.de"))
    }

    @Test
    fun charactersThatMapToNothingAreDropped() {
        assertEquals("ab.de", Nameprep.prepare("a\u00ADb.de"))
        assertEquals("ab.de", Nameprep.prepare("a\u200Bb.de"))
        assertEquals("ab.de", Nameprep.prepare("a\uFE0Fb.de"))
    }

    @Test
    fun theIdeographicDotsSeparateLabels() {
        assertEquals("m\u00FCnchen.de", Nameprep.prepare("m\u00FCnchen\u3002de"))
        assertEquals("m\u00FCnchen.de", Nameprep.prepare("m\u00FCnchen\uFF0Ede"))
        assertEquals("m\u00FCnchen.de", Nameprep.prepare("m\u00FCnchen\uFF61de"))
    }

    @Test
    fun prohibitedCharactersMakeTheNameInvalid() {
        assertNull(Nameprep.prepare("a\u1680b\u00E4.de"), "a non-ASCII space")
        assertNull(Nameprep.prepare("a\u0085\u00E4.de"), "a control")
        assertNull(Nameprep.prepare("a\uE000\u00E4.de"), "private use")
        assertNull(Nameprep.prepare("a\u202E\u00E4.de"), "a bidi control")
        assertNull(Nameprep.prepare("a\uFDD0\u00E4.de"), "a non-character")
    }

    @Test
    fun aLabelThatPassesForAnEncodedOneIsRefused() {
        assertNull(Nameprep.prepare("xn--\u00E4.de"))
    }
}
