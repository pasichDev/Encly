package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretRule
import java.text.Normalizer

/**
 * The canonical form of a desktop password and the rules around it (slot version 4).
 *
 * The password is normalised ONCE, by [canonical], to NFC (unpaired surrogates rejected); everything after that
 * works on that form: [utf8] encodes it for the Argon2 and HMAC inputs (the same bytes in both, so the two cannot
 * disagree), [checkForSet] counts it. The same text typed as precomposed (NFC) or decomposed (NFD) letters, which
 * keyboards and platforms differ on, gives the same bytes and so the same key. The v3 PIN handling
 * (`c.code.toByte()`, which truncates non-ASCII) is deliberately not reused.
 *
 * Memory: every array here belongs to the caller or is wiped before the call returns. What cannot be wiped is a
 * `String`: the JDK normalizer takes and returns only immutable `String`s (`Normalizer.normalize(CharSequence)` calls
 * `toString()` on its input), so a password with a character from U+0300 up (a combining mark, Cyrillic, Greek, CJK,
 * an emoji ...) is copied into two `String`s that stay in the heap until the garbage collector takes them. A password
 * of the characters below U+0300 (ASCII, Western European letters) is already in NFC and never becomes a `String`:
 * [canonical] sees that by itself. The UTF-8 encoder is our own for the same reason: `CharsetEncoder` and
 * `String.toByteArray` leave resized copies behind.
 */
internal object PasswordCanonical {
    const val UNLOCK_MIN_UNITS = 1
    const val UNLOCK_MAX_UNITS = 256

    const val SET_MIN_CODE_POINTS = 12
    const val SET_MAX_CODE_POINTS = 128
    const val SET_MIN_DISTINCT = 5

    /**
     * The most UTF-16 units of raw typing that are normalised at all: 128 code points (the longest password that may
     * be set) times 6, the most units any one character of an NFC text expands to in NFD (the longest form a
     * canonically equivalent typing can have). 6 is the maximum over all of Unicode 17 (Python's `unicodedata`; for
     * example U+16D6A); the JDK's own, older tables give at most 4 (U+1F82), which a test checks. It only limits
     * the work of normalising what was pasted; the rule that counts is [UNLOCK_MAX_UNITS] on the NFC form.
     */
    const val TYPED_MAX_UNITS = SET_MAX_CODE_POINTS * 6

    /** Every character below this one is its own NFC form and composes with nothing before it (NFC_Quick_Check=Yes). */
    private const val FIRST_COMBINING = '\u0300'
    private const val ONE_BYTE_LIMIT = 0x80
    private const val TWO_BYTE_LIMIT = 0x800
    private const val THREE_BYTE_LIMIT = 0x10000
    private const val TWO_BYTES = 2
    private const val THREE_BYTES = 3
    private const val FOUR_BYTES = 4
    private const val LEAD_TWO = 0xC0
    private const val LEAD_THREE = 0xE0
    private const val LEAD_FOUR = 0xF0
    private const val CONTINUATION = 0x80
    private const val CONTINUATION_MASK = 0x3F
    private const val SIX_BITS = 6
    private const val TWELVE_BITS = 12
    private const val EIGHTEEN_BITS = 18

    /** Whether raw typing is short enough to be normalised: 1 to [TYPED_MAX_UNITS] units. */
    fun withinTypedCap(typed: CharArray): Boolean = typed.size in UNLOCK_MIN_UNITS..TYPED_MAX_UNITS

    /**
     * The rule when UNLOCKING, on the NFC form [canonical] made: 1 to [UNLOCK_MAX_UNITS] UTF-16 units. It is wider
     * than the rule for setting (12 to 128 code points), so that a later tightening of that rule locks nobody out, and
     * it holds for any typing of a password that may be set, however decomposed.
     */
    fun withinUnlockBounds(nfc: CharArray): Boolean = nfc.size in UNLOCK_MIN_UNITS..UNLOCK_MAX_UNITS

    /**
     * A fresh array with the NFC form of [typed] (the caller wipes it), or null when [typed] has an unpaired
     * surrogate. [typed] is not touched. See the class comment for what the JDK leaves behind.
     */
    fun canonical(typed: CharArray): CharArray? = when {
        hasUnpairedSurrogate(typed) -> null
        typed.all { it < FIRST_COMBINING } -> typed.copyOf()
        else -> Normalizer.normalize(String(typed), Normalizer.Form.NFC).toCharArray()
    }

    /**
     * `UTF-8` of [canonical] (strict: no unpaired surrogate may be in it) in an exact-size array, with no copy made on
     * the way; the caller wipes it. Throws [IllegalArgumentException] for an unpaired surrogate.
     */
    fun utf8(canonical: CharArray): ByteArray {
        require(!hasUnpairedSurrogate(canonical)) { "Not text: an unpaired surrogate" }
        var size = 0
        var i = 0
        while (i < canonical.size) {
            val cp = Character.codePointAt(canonical, i)
            size += utf8Size(cp)
            i += Character.charCount(cp)
        }
        val out = ByteArray(size)
        var o = 0
        i = 0
        while (i < canonical.size) {
            val cp = Character.codePointAt(canonical, i)
            i += Character.charCount(cp)
            o = writeUtf8(out, o, cp)
        }
        return out
    }

    /**
     * The rule when SETTING a password, over the NFC form [canonical] gave: [SET_MIN_CODE_POINTS] to
     * [SET_MAX_CODE_POINTS] code points (an emoji or any other supplementary character counts once), not digits only,
     * and at least [SET_MIN_DISTINCT] distinct code points. Nothing is normalised here.
     */
    fun checkForSet(canonical: CharArray): SecretRule {
        val count = Character.codePointCount(canonical, 0, canonical.size)
        return when {
            count < SET_MIN_CODE_POINTS -> SecretRule.TOO_SHORT
            count > SET_MAX_CODE_POINTS -> SecretRule.TOO_LONG
            isSimple(canonical, count) -> SecretRule.TOO_SIMPLE
            else -> SecretRule.OK
        }
    }

    /** True when every UTF-16 high surrogate of [chars] is followed by a low one and no low one stands alone. */
    fun hasUnpairedSurrogate(chars: CharArray): Boolean {
        var unpaired = false
        var i = 0
        while (i < chars.size && !unpaired) {
            val c = chars[i]
            if (Character.isHighSurrogate(c)) {
                unpaired = i + 1 >= chars.size || !Character.isLowSurrogate(chars[i + 1])
                i++
            } else {
                unpaired = Character.isLowSurrogate(c)
            }
            i++
        }
        return unpaired
    }

    private fun isSimple(normalized: CharArray, count: Int): Boolean {
        val codePoints = IntArray(count)
        try {
            var index = 0
            var at = 0
            while (at < normalized.size) {
                val cp = Character.codePointAt(normalized, at)
                codePoints[index++] = cp
                at += Character.charCount(cp)
            }
            if (codePoints.all { Character.isDigit(it) }) return true
            codePoints.sort()
            var distinct = 1
            for (i in 1 until codePoints.size) if (codePoints[i] != codePoints[i - 1]) distinct++
            return distinct < SET_MIN_DISTINCT
        } finally {
            codePoints.fill(0)
        }
    }

    private fun utf8Size(cp: Int): Int = when {
        cp < ONE_BYTE_LIMIT -> 1
        cp < TWO_BYTE_LIMIT -> TWO_BYTES
        cp < THREE_BYTE_LIMIT -> THREE_BYTES
        else -> FOUR_BYTES
    }

    private fun writeUtf8(out: ByteArray, at: Int, cp: Int): Int {
        var o = at
        when {
            cp < ONE_BYTE_LIMIT -> out[o++] = cp.toByte()

            cp < TWO_BYTE_LIMIT -> {
                out[o++] = (LEAD_TWO or (cp shr SIX_BITS)).toByte()
                out[o++] = (CONTINUATION or (cp and CONTINUATION_MASK)).toByte()
            }

            cp < THREE_BYTE_LIMIT -> {
                out[o++] = (LEAD_THREE or (cp shr TWELVE_BITS)).toByte()
                out[o++] = (CONTINUATION or ((cp shr SIX_BITS) and CONTINUATION_MASK)).toByte()
                out[o++] = (CONTINUATION or (cp and CONTINUATION_MASK)).toByte()
            }

            else -> {
                out[o++] = (LEAD_FOUR or (cp shr EIGHTEEN_BITS)).toByte()
                out[o++] = (CONTINUATION or ((cp shr TWELVE_BITS) and CONTINUATION_MASK)).toByte()
                out[o++] = (CONTINUATION or ((cp shr SIX_BITS) and CONTINUATION_MASK)).toByte()
                out[o++] = (CONTINUATION or (cp and CONTINUATION_MASK)).toByte()
            }
        }
        return o
    }
}
