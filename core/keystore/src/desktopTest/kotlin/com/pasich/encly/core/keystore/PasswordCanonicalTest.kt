package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretRule
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The expected NFC forms and bytes below are literals computed outside the JDK, with Python's `unicodedata`
 * (`gen/canonical_literals.py` of the scratchpad), so that a fault in the JDK's normalizer, the code under test, cannot
 * make a test pass. The two tests that still ask the JDK say so.
 */
class PasswordCanonicalTest {
    /** The bytes the keys are made from: NFC once, then UTF-8. */
    private fun canonical(text: String): ByteArray? =
        PasswordCanonical.canonical(text.toCharArray())?.let(PasswordCanonical::utf8)

    /** The rule for setting, over the NFC form (what the policy hands it). */
    private fun forSet(text: String) = PasswordCanonical.checkForSet(PasswordCanonical.canonical(text.toCharArray())!!)

    private fun withinUnlock(text: String) = PasswordCanonical.withinUnlockBounds(text.toCharArray())

    private fun withinCap(units: Int) = PasswordCanonical.withinTypedCap(CharArray(units) { 'a' })

    private fun emoji(count: Int): String = (0 until count).joinToString("") { String(Character.toChars(0x1F600 + it)) }

    // The cap on raw typing

    @Test
    fun rawTypingIsCappedAtSevenHundredSixtyEightUnitsOnlyToLimitTheWorkOfNormalising() {
        assertEquals(768, PasswordCanonical.TYPED_MAX_UNITS)
        assertEquals(128 * 6, PasswordCanonical.TYPED_MAX_UNITS)
        assertFalse(withinCap(0))
        assertTrue(withinCap(1))
        assertTrue(withinCap(768))
        assertFalse(withinCap(769))
    }

    @Test
    fun noCharacterOfTheJdksTablesExpandsToMoreThanTheSixUnitsTheCapAssumes() {
        // The JDK knows an older Unicode than the six units were taken from (Python: 6, U+16D6A): this is a premise
        // check against the platform that actually normalises, not the source of the number.
        var most = 0
        for (code in 0..Character.MAX_CODE_POINT) {
            if (code in 0xD800..0xDFFF) continue
            val text = String(Character.toChars(code))
            if (Normalizer.normalize(text, Normalizer.Form.NFC) == text) {
                most = maxOf(most, Normalizer.normalize(text, Normalizer.Form.NFD).length)
            }
        }
        assertTrue(most <= 6, "most is $most")
    }

    // The canonical form

    @Test
    fun anAsciiPasswordIsItsOwnBytes() {
        val text = "correct horse battery staple"

        assertContentEquals(text.toByteArray(Charsets.US_ASCII), canonical(text))
    }

    @Test
    fun aNonAsciiPasswordGivenInNfcAndInNfdGivesTheSameBytes() {
        val nfc = "\u041F\u0440\u0438\u0432\u0456\u0442, caf\u00E9 \u2014 \u65E5\u672C\u8A9E"
        val nfd = "\u041F\u0440\u0438\u0432\u0456\u0442, cafe\u0301 \u2014 \u65E5\u672C\u8A9E"
        assertNotEquals(nfc, nfd, "the fixture must differ before normalization")

        val fromNfc = assertNotNull(canonical(nfc))
        val fromNfd = assertNotNull(canonical(nfd))

        assertEquals(MIXED_UTF8, fromNfc.toHex())
        assertEquals(MIXED_UTF8, fromNfd.toHex())
    }

    @Test
    fun theEncoderGivesTheKnownBytesAtTheEdgeOfEveryRange() {
        // U+007F, U+0080, U+07FF, U+0800, U+FFFF, U+10000, U+10FFFF, U+1F600.
        val text = "a\u007F\u0080\u07FF\u0800\uFFFF\uD800\uDC00\uDBFF\uDFFF\uD83D\uDE00"

        assertEquals("617fc280dfbfe0a080efbfbff0908080f48fbfbff09f9880", canonical(text)!!.toHex())
    }

    @Test
    fun theCallersArrayIsNotTouched() {
        val typed = "Здоровенькі були".toCharArray()
        val copy = typed.copyOf()

        PasswordCanonical.canonical(typed)
        PasswordCanonical.checkForSet(typed)
        PasswordCanonical.utf8(typed)

        assertContentEquals(copy, typed)
    }

    @Test
    fun anUnpairedSurrogateIsRejected() {
        assertNull(canonical("abc\uD800def"))
        assertNull(canonical("abc\uDC00def"))
        assertNull(canonical("trailing high\uD83D"))
        assertNull(canonical("\uDE00\uD83D reversed"))
        assertNotNull(canonical("paired 😀 is fine"))
    }

    @Test
    fun anEmptyPasswordHasEmptyBytes() {
        assertContentEquals(ByteArray(0), canonical(""))
    }

    @Test
    fun theCallersArrayOfAnNfdPasswordIsNotTouchedEither() {
        val typed = "cafe\u0301".toCharArray()
        val copy = typed.copyOf()

        PasswordCanonical.canonical(typed)

        assertContentEquals(copy, typed)
    }

    // The rule for unlocking

    @Test
    fun unlockingAcceptsOneToTwoHundredFiftySixUnits() {
        assertFalse(withinUnlock(""))
        assertTrue(withinUnlock("a"))
        assertTrue(withinUnlock("a".repeat(256)))
        assertFalse(withinUnlock("a".repeat(257)))
    }

    @Test
    fun unlockingCountsUtf16UnitsNotCodePoints() {
        assertTrue(withinUnlock("😀".repeat(128)))
        assertFalse(withinUnlock("😀".repeat(129)))
    }

    @Test
    fun unlockingAcceptsAPasswordTheSetRuleWouldRefuse() {
        assertTrue(withinUnlock("1"))
        assertTrue(withinUnlock("aaaa"))
    }

    // The rule for setting

    @Test
    fun aGoodPasswordPasses() {
        assertEquals(SecretRule.OK, forSet("correct horse battery staple"))
        assertEquals(SecretRule.OK, forSet("Привіт, світе! 123"))
    }

    @Test
    fun twelveCodePointsIsTheMinimum() {
        assertEquals(SecretRule.OK, forSet("abcdefghijkl"))
        assertEquals(SecretRule.TOO_SHORT, forSet("abcdefghijk"))
        assertEquals(SecretRule.TOO_SHORT, forSet(""))
    }

    @Test
    fun oneHundredTwentyEightCodePointsIsTheMaximum() {
        assertEquals(SecretRule.OK, forSet("abcdefgh".repeat(16)))
        assertEquals(SecretRule.TOO_LONG, forSet("abcdefgh".repeat(16) + "c"))
    }

    @Test
    fun supplementaryCodePointsCountAsOne() {
        // Eleven emoji are 22 UTF-16 units but 11 code points.
        assertEquals(SecretRule.TOO_SHORT, forSet(emoji(11)))
        assertEquals(SecretRule.OK, forSet(emoji(12)))
        // 128 emoji are 256 units and exactly the maximum.
        assertEquals(SecretRule.OK, forSet(emoji(128)))
        assertEquals(SecretRule.TOO_LONG, forSet(emoji(129)))
        assertEquals(SecretRule.TOO_SIMPLE, forSet("😀".repeat(12)))
    }

    @Test
    fun theLengthIsMeasuredAfterNfc() {
        val twelve = "a\u0301e\u0301i\u0301o\u0301u\u0301a\u0300e\u0300i\u0300o\u0300u\u0300a\u0302e\u0302"
        val eleven = "a\u0301e\u0301i\u0301o\u0301u\u0301a\u0300e\u0300i\u0300o\u0300u\u0300a\u0302"
        // In NFD both are longer than the bound in UTF-16 units; after NFC they are 12 and 11 code points.
        assertTrue(eleven.length > 12)

        assertEquals(SecretRule.OK, forSet(twelve))
        assertEquals(SecretRule.TOO_SHORT, forSet(eleven))
    }

    @Test
    fun digitsOnlyIsTooSimple() {
        assertEquals(SecretRule.TOO_SIMPLE, forSet("123456789012"))
        assertEquals(SecretRule.TOO_SIMPLE, forSet("0123456789".repeat(5)))
        // Digits of other scripts are digits too (Arabic-Indic).
        assertEquals(SecretRule.TOO_SIMPLE, forSet("١٢٣٤٥٦٧٨٩١٢٣"))
        assertEquals(SecretRule.OK, forSet("12345678901a"))
    }

    @Test
    fun fewerThanFiveDistinctCodePointsIsTooSimple() {
        assertEquals(SecretRule.TOO_SIMPLE, forSet("aaaaaaaaaaaa"))
        assertEquals(SecretRule.TOO_SIMPLE, forSet("abababababab"))
        assertEquals(SecretRule.TOO_SIMPLE, forSet("abcdabcdabcd"))
        assertEquals(SecretRule.OK, forSet("abcdeabcdeab"))
    }

    @Test
    fun distinctCountsEmojiOneEach() {
        assertEquals(SecretRule.TOO_SIMPLE, forSet(emoji(4).repeat(3)))
        assertEquals(SecretRule.OK, forSet(emoji(5).repeat(3)))
    }

    @Test
    fun anUnpairedSurrogateHasNoCanonicalFormSoItCannotBeSet() {
        assertNull(PasswordCanonical.canonical("abcdefghijkl\uD800".toCharArray()))
        assertNull(PasswordCanonical.canonical("\uDC00abcdefghijkl".toCharArray()))
    }

    @Test
    fun theUtf8EncoderRefusesAnUnpairedSurrogate() {
        assertFailsWith<IllegalArgumentException> { PasswordCanonical.utf8("ab\uD800".toCharArray()) }
        assertContentEquals(ByteArray(0), PasswordCanonical.utf8(CharArray(0)))
    }

    @Test
    fun everyCharacterBelowTheFirstCombiningMarkIsItsOwnNfcFormSoTheJdkIsNotAsked() {
        for (code in 0 until 0x300) {
            val text = code.toChar().toString()
            if (code in 0xD800..0xDFFF) continue
            assertTrue(Normalizer.isNormalized(text, Normalizer.Form.NFC), "U+%04X".format(code))
            assertEquals(text, String(PasswordCanonical.canonical(text.toCharArray())!!))
        }
        // A character of the next block is where the JDK has to be asked.
        assertEquals("\u00E9", String(PasswordCanonical.canonical("e\u0301".toCharArray())!!))
        assertEquals("\u0301", String(PasswordCanonical.canonical("\u0301".toCharArray())!!))
    }

    @Test
    fun theNormalizedFormIsAFreshArrayAndTheTypedOneIsLeftAlone() {
        val typed = "plain ascii".toCharArray()

        val canonical = PasswordCanonical.canonical(typed)!!
        canonical.fill('x')

        assertEquals("plain ascii", String(typed))
    }

    @Test
    fun hasUnpairedSurrogateSeesEveryPosition() {
        assertFalse(PasswordCanonical.hasUnpairedSurrogate("".toCharArray()))
        assertFalse(PasswordCanonical.hasUnpairedSurrogate("😀a😀".toCharArray()))
        assertTrue(PasswordCanonical.hasUnpairedSurrogate("\uD83D".toCharArray()))
        assertTrue(PasswordCanonical.hasUnpairedSurrogate("\uDE00".toCharArray()))
        assertTrue(PasswordCanonical.hasUnpairedSurrogate("\uD83D😀".toCharArray()))
    }

    private companion object {
        const val MIXED_UTF8 = "d09fd180d0b8d0b2d196d1822c20636166c3a920e2809420e697a5e69cace8aa9e"
    }
}
