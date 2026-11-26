package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.security.PinSecretPolicy
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.testutil.FakePassphrasePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the screens ask of the policy while a secret is chosen: the kind, why one is refused, and a suggestion. */
class SecretChoiceTest {
    private val fake = FakePassphrasePolicy()
    private val passphrase = SecretChoice(fake)
    private val pin = SecretChoice(PinSecretPolicy())

    private fun refusal(typed: String) = passphrase.refusal(typed.toCharArray())

    @Test
    fun aPassphraseOverWhatUnlockTakesIsTooLongNotUnusable() {
        val long = "x".repeat(300)
        val policy = object : com.pasich.encly.core.security.UnlockSecretPolicy by FakePassphrasePolicy() {
            // As the desktop policy: unlock takes no more than 256 units, choosing is told why it is too much.
            override fun canonical(typed: CharArray): CharArray? = typed.takeIf { it.size <= 256 }?.copyOf()

            override fun canonicalForSetting(typed: CharArray): CharArray? = typed.copyOf()

            override fun mayBeSet(canonical: CharArray): SecretRule =
                if (canonical.size > 128) SecretRule.TOO_LONG else SecretRule.OK
        }

        assertEquals(SecretRefusal.TOO_LONG, SecretChoice(policy).refusal(long.toCharArray()))
    }

    @Test
    fun theKindIsTheOneOfThePolicy() {
        assertEquals(SecretKind.PASSWORD, passphrase.kind)
        assertEquals(SecretKind.PIN, pin.kind)
    }

    @Test
    fun aGoodPassphraseIsNotRefused() {
        assertNull(refusal(FakePassphrasePolicy.GOOD))
    }

    @Test
    fun aShortOneIsTooShort() {
        assertEquals(SecretRefusal.TOO_SHORT, refusal("only eleven"))
    }

    @Test
    fun digitsOnlyAndTooFewDifferentCharactersAreTooSimple() {
        assertEquals(SecretRefusal.TOO_SIMPLE, refusal("123456789012"))
        assertEquals(SecretRefusal.TOO_SIMPLE, refusal("abababababab"))
    }

    @Test
    fun nothingTypedIsUnusable() {
        assertEquals(SecretRefusal.UNUSABLE, refusal(""))
    }

    @Test
    fun whatThePolicyCallsInvalidIsUnusable() {
        fake.forcedRule = SecretRule.INVALID

        assertEquals(SecretRefusal.UNUSABLE, refusal(FakePassphrasePolicy.GOOD))
    }

    @Test
    fun whatThePolicyCallsTooLongIsTooLong() {
        fake.forcedRule = SecretRule.TOO_LONG

        assertEquals(SecretRefusal.TOO_LONG, refusal(FakePassphrasePolicy.GOOD))
    }

    @Test
    fun theLongestPassphraseIsAllowedAndOneMoreIsTooLong() {
        assertNull(refusal("abcdefgh".repeat(16)))
        assertEquals(SecretRefusal.TOO_LONG, refusal("abcdefgh".repeat(16) + "i"))
    }

    @Test
    fun theLengthIsCountedInCharactersNotInHalvesOfAPair() {
        // 70 characters of two halves each: more than 128 chars, but 70 characters.
        assertEquals(SecretRefusal.TOO_SIMPLE, refusal("😀".repeat(70)))
    }

    @Test
    fun everyCopyTheRuleWasAskedAboutIsWiped() {
        refusal(FakePassphrasePolicy.GOOD)
        refusal("short")

        assertTrue(fake.canonicals.isNotEmpty())
        assertTrue(fake.canonicals.all { copy -> copy.all { it == '\u0000' } })
    }

    @Test
    fun aPinIsNeverRefusedHereAndTheRuleIsNotAsked() {
        assertNull(pin.refusal("1".toCharArray()))
        assertNull(pin.refusal("123456".toCharArray()))
    }

    @Test
    fun twoEntriesAreTheSameWhenTheirCanonicalFormsAre() {
        assertTrue(passphrase.same("correct horse".toCharArray(), "correct horse".toCharArray()))
        assertTrue(!passphrase.same("correct horse".toCharArray(), "correct horsf".toCharArray()))
        // The policy refuses to make a secret of nothing: two empty entries are not the same secret.
        assertTrue(!passphrase.same(CharArray(0), CharArray(0)))
        assertTrue(fake.canonicals.all { copy -> copy.all { it == '\u0000' } })
    }

    @Test
    fun twoSpellingsOfTheSameTextAreTheSameSecret() {
        val precomposed = "caf\u00e9 au lait \u00e7a va".toCharArray()
        val decomposed = "cafe\u0301 au lait c\u0327a va".toCharArray()

        // A plain comparison tells them apart, which is what the policy's canonical form is for.
        assertTrue(!precomposed.contentEquals(decomposed))
        assertTrue(passphrase.same(precomposed, decomposed))
    }

    @Test
    fun differentCharactersAreCountedByCodePointNotByHalvesOfAPair() {
        // Four emoji of three copies each: 24 units, 12 characters, and only 4 different ones.
        val four = "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03"

        assertEquals(SecretRefusal.TOO_SIMPLE, refusal(four.repeat(3)))
    }

    @Test
    fun anEntryThePolicyCannotMakeASecretOfIsNotTheSameAsAnything() {
        assertTrue(!passphrase.same(FakePassphrasePolicy.GOOD.toCharArray(), CharArray(0)))
        assertTrue(!passphrase.same(CharArray(0), FakePassphrasePolicy.GOOD.toCharArray()))
        assertTrue(fake.canonicals.all { copy -> copy.all { it == '\u0000' } })
    }

    @Test
    fun twoPinsAreTheSameWhenTheirDigitsAre() {
        assertTrue(pin.same("123456".toCharArray(), "123456".toCharArray()))
        assertTrue(!pin.same("123456".toCharArray(), "123457".toCharArray()))
    }

    @Test
    fun aPassphraseIsSuggestedAndAPinIsNot() {
        val suggestion = passphrase.suggest()

        assertNotNull(suggestion)
        assertEquals(FakePassphrasePolicy.SUGGESTION, String(suggestion!!))
        assertNull(passphrase.refusal(suggestion))
        assertNull(pin.suggest())
    }
}
