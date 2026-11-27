package com.pasich.encly.testutil

import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.SlotHeader
import com.pasich.encly.core.security.UnlockSecretPolicy

/**
 * A passphrase policy for the screens' tests (nothing is stretched here): the rule of the desktop one, 12 to 128
 * characters (a pair of surrogate halves counts once), not digits only, at least five different characters, and a
 * suggestion of five words. Its canonical form is composed (e and an acute accent are one letter), as the real
 * policy normalises to NFC. It counts what it
 * was asked, so a test can see which calls a screen made, and keeps every canonical copy it handed out, so a test
 * can see that the screen wiped them.
 */
internal class FakePassphrasePolicy(private val suggestion: String = SUGGESTION) : UnlockSecretPolicy {
    var canonicalCalls = 0
        private set
    var suggestCalls = 0
        private set
    val canonicals = mutableListOf<CharArray>()

    /** Set to make the rule say this, whatever is asked (the answers the real rule gives only for odd input). */
    var forcedRule: SecretRule? = null

    override val kind = SecretKind.PASSWORD
    override val slotVersion = SlotHeader.PASSWORD_VERSION
    override val chargesForUnusableSlot = false
    override val maxTypedUnits = MAX_TYPED
    override val kekInfo = "test/kek".encodeToByteArray()
    override val wipeInfo = "test/wipe".encodeToByteArray()
    override val decoyInfo = "test/decoy".encodeToByteArray()

    override fun canonical(typed: CharArray): CharArray? {
        canonicalCalls++
        return if (typed.isEmpty() || typed.size > MAX_TYPED) null else compose(typed).also { canonicals += it }
    }

    /** What NFC does to the few pairs the tests use: a letter and its combining mark become the one letter. */
    private fun compose(chars: CharArray): CharArray {
        val out = StringBuilder()
        var i = 0
        while (i < chars.size) {
            val composed = chars.getOrNull(i + 1)?.let { COMPOSITIONS[chars[i] to it] }
            out.append(composed ?: chars[i])
            i += if (composed == null) 1 else 2
        }
        return out.toString().toCharArray()
    }

    private fun codePoints(chars: CharArray): List<Int> {
        val points = mutableListOf<Int>()
        var i = 0
        while (i < chars.size) {
            val high = chars[i]
            val low = chars.getOrNull(i + 1)
            if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
                points += 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
                i += 2
            } else {
                points += high.code
                i++
            }
        }
        return points
    }

    override fun mayBeSet(canonical: CharArray): SecretRule = forcedRule ?: when {
        characters(canonical) < MIN_LENGTH -> SecretRule.TOO_SHORT
        characters(canonical) > MAX_LENGTH -> SecretRule.TOO_LONG
        canonical.all { it in '0'..'9' } || codePoints(canonical).toSet().size < MIN_DIFFERENT -> SecretRule.TOO_SIMPLE
        else -> SecretRule.OK
    }

    private fun characters(chars: CharArray): Int = chars.count { !it.isLowSurrogate() }

    override fun newHeader(salt: ByteArray): SlotHeader =
        SlotHeader(slotVersion, SlotHeader.KDF_ARGON2ID, SlotHeader.FACTOR_OS_STORE_HMAC, 65_536L, 3, 1, salt)

    override fun stretch(canonical: CharArray, header: SlotHeader): ByteArray = ByteArray(0)

    override fun factorInput(canonical: CharArray, salt: ByteArray): ByteArray = salt

    override fun accepts(header: SlotHeader): Boolean = true

    override fun suggest(): CharArray? {
        suggestCalls++
        return suggestion.toCharArray()
    }

    companion object {
        const val SUGGESTION = "orbit cactus velvet maple lantern"
        private val COMPOSITIONS = mapOf(
            ('e' to '\u0301') to '\u00e9',
            ('c' to '\u0327') to '\u00e7',
            ('a' to '\u0308') to '\u00e4',
        )
        const val MIN_LENGTH = 12
        const val MAX_LENGTH = 128
        const val MIN_DIFFERENT = 5
        const val MAX_TYPED = 256

        /** A passphrase the rule accepts. */
        const val GOOD = "correct horse battery"
    }
}
