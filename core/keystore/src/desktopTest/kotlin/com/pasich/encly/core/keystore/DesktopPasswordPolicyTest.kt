package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.SecretStretchException
import com.pasich.encly.core.security.SlotHeader
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What [DesktopPasswordPolicy] says on its own, with a recording engine in the place of Argon2id. */
class DesktopPasswordPolicyTest {
    /**
     * One call of the engine: the password array it was handed (to see that it was wiped afterwards) and what it held
     * at the time of the call.
     */
    private class Call(val password: ByteArray, val salt: ByteArray, val memory: Int, val passes: Int, val lanes: Int) {
        val passwordAtCall: ByteArray = password.copyOf()
    }

    private val calls = mutableListOf<Call>()
    private val policy = DesktopPasswordPolicy { password, salt, memory, passes, lanes ->
        calls += Call(password, salt.copyOf(), memory, passes, lanes)
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256"))
            doFinal(password)
        }
    }

    private val salt = ByteArray(16) { it.toByte() }

    private fun header(
        version: Byte = 4,
        kdf: Int = 2,
        factor: Int = 1,
        memory: Long = 65_536,
        passes: Int = 3,
        lanes: Int = 1,
        salt: ByteArray = this.salt,
    ) = SlotHeader(version, kdf, factor, memory, passes, lanes, salt)

    private fun emoji(count: Int): String = (0 until count).joinToString("") { String(Character.toChars(0x1F600 + it)) }

    // What it is

    @Test
    fun itIsThePasswordPolicyOfSlotVersion4WithTheVersion4Infos() {
        assertEquals(SecretKind.PASSWORD, policy.kind)
        assertEquals(4, policy.slotVersion.toInt())
        assertEquals("encly/secret/kek/v4", String(policy.kekInfo))
        assertEquals("encly/secret/wipe/v4", String(policy.wipeInfo))
        assertEquals("encly/secret/decoy/v4", String(policy.decoyInfo))
    }

    @Test
    fun theInfosAreCopiesThatCanBeWipedWithoutHarm() {
        policy.kekInfo.fill(0)
        policy.wipeInfo.fill(0)
        policy.decoyInfo.fill(0)

        assertEquals("encly/secret/kek/v4", String(policy.kekInfo))
        assertEquals("encly/secret/wipe/v4", String(policy.wipeInfo))
        assertEquals("encly/secret/decoy/v4", String(policy.decoyInfo))
    }

    @Test
    fun aNewHeaderIsArgon2idAtTheProductionCostAroundTheGivenSalt() {
        val fresh = policy.newHeader(salt)

        assertEquals(4, fresh.version.toInt())
        assertEquals(2, fresh.kdfId)
        assertEquals(1, fresh.factorId)
        assertEquals(65_536L, fresh.memoryKiB)
        assertEquals(3, fresh.passes)
        assertEquals(1, fresh.lanes)
        assertSame(salt, fresh.salt)
        assertTrue(policy.accepts(fresh))
    }

    // canonical

    @Test
    fun unlockingTakesOneToTwoHundredFiftySixUnitsOfAnything() {
        assertNull(policy.canonical(CharArray(0)))
        assertNotNull(policy.canonical("a".toCharArray()))
        assertNotNull(policy.canonical("1".toCharArray()))
        assertNotNull(policy.canonical("a".repeat(256).toCharArray()))
        assertNull(policy.canonical("a".repeat(257).toCharArray()))
        assertNotNull(policy.canonical(emoji(128).toCharArray()))
        assertNull(policy.canonical(emoji(129).toCharArray()))
    }

    @Test
    fun theBoundIsOnTheNfcFormAndRawTypingIsOnlyCappedForTheWorkOfNormalising() {
        assertEquals(768, policy.maxTypedUnits)
        // 256 letters of three units each: exactly 768 typed, 256 in NFC.
        val atTheCap = "e\u0323\u0302".repeat(256)
        assertEquals(768, atTheCap.length)
        assertEquals(256, policy.canonical(atTheCap.toCharArray())!!.size)
        // One more unit than the cap.
        assertNull(policy.canonical((atTheCap + "e").toCharArray()))
        // Under the cap, but 257 units once normalised.
        assertNull(policy.canonical("a\u0301".repeat(257).toCharArray()))
    }

    @Test
    fun aLoneSurrogateCanNotBeTyped() {
        assertNull(policy.canonical("abc\uD800def".toCharArray()))
        assertNull(policy.canonical("abc\uDC00def".toCharArray()))
        assertNotNull(policy.canonical("paired 😀 is fine".toCharArray()))
    }

    @Test
    fun theCanonicalFormIsNfcMadeOnceAndAFreshCopy() {
        val nfc = Normalizer.normalize("Привіт, café — 日本語", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD)
        assertTrue(nfc != nfd)
        val typed = nfd.toCharArray()

        val canonical = assertNotNull(policy.canonical(typed))

        assertEquals(nfc, String(canonical))
        assertNotSame(typed, canonical)
        assertEquals(nfd, String(typed))
        val plain = "plain ascii".toCharArray()
        assertNotSame(plain, policy.canonical(plain))
    }

    // mayBeSet

    @Test
    fun aPasswordIsTwelveToOneHundredTwentyEightCodePointsNotDigitsOnlyWithFiveDistinct() {
        assertEquals(SecretRule.TOO_SHORT, set("abcdefghijk"))
        assertEquals(SecretRule.OK, set("abcdefghijkl"))
        assertEquals(SecretRule.OK, set("abcdefgh".repeat(16)))
        assertEquals(SecretRule.TOO_LONG, set("abcdefgh".repeat(16) + "c"))
        assertEquals(SecretRule.TOO_SIMPLE, set("123456789012"))
        assertEquals(SecretRule.TOO_SIMPLE, set("١٢٣٤٥٦٧٨٩١٢٣"))
        assertEquals(SecretRule.OK, set("12345678901a"))
        assertEquals(SecretRule.TOO_SIMPLE, set("abcdabcdabcd"))
        assertEquals(SecretRule.OK, set("abcdeabcdeab"))
        assertEquals(SecretRule.TOO_SHORT, set(emoji(11)))
        assertEquals(SecretRule.OK, set(emoji(12)))
        assertEquals(SecretRule.OK, set(emoji(128)))
        // 129 emoji are 258 units: more than the lock screen takes, so there is no canonical form to judge.
        assertNull(policy.canonical(emoji(129).toCharArray()))
        assertEquals(SecretRule.TOO_SIMPLE, set("😀".repeat(12)))
    }

    @Test
    fun theLengthIsMeasuredOnTheNfcFormTheManagerHandsIn() {
        val twelve = Normalizer.normalize("áéíóúàèìòùâê", Normalizer.Form.NFD)
        val eleven = Normalizer.normalize("áéíóúàèìòùâ", Normalizer.Form.NFD)

        assertEquals(SecretRule.OK, policy.mayBeSet(policy.canonical(twelve.toCharArray())!!))
        assertEquals(SecretRule.TOO_SHORT, policy.mayBeSet(policy.canonical(eleven.toCharArray())!!))
    }

    private fun set(text: String): SecretRule = policy.mayBeSet(policy.canonical(text.toCharArray())!!)

    // accepts

    @Test
    fun aHeaderIsAcceptedOnlyInsideTheBoundsOnBothSidesOfEveryOne() {
        assertTrue(policy.accepts(header()))
        assertTrue(policy.accepts(header(memory = 65_536, passes = 3)))
        assertTrue(policy.accepts(header(memory = 1_048_576, passes = 10)))

        val refused = mapOf(
            "version 3" to header(version = 3),
            "version 5" to header(version = 5),
            "kdf PBKDF2" to header(kdf = 1),
            "kdf 3" to header(kdf = 3),
            "kdf 0" to header(kdf = 0),
            "factor 0" to header(factor = 0),
            "factor 2" to header(factor = 2),
            "memory 65535" to header(memory = 65_535),
            "memory 1048577" to header(memory = 1_048_577),
            "memory 0" to header(memory = 0),
            "memory u32 max" to header(memory = 0xFFFF_FFFFL),
            "memory 2^32 + 65536" to header(memory = (1L shl 32) + 65_536),
            "passes 2" to header(passes = 2),
            "passes 11" to header(passes = 11),
            "passes 0" to header(passes = 0),
            "passes 255" to header(passes = 255),
            "lanes 0" to header(lanes = 0),
            "lanes 2" to header(lanes = 2),
            "lanes 255" to header(lanes = 255),
            "salt 15" to header(salt = ByteArray(15)),
            "salt 17" to header(salt = ByteArray(17)),
        )
        for ((name, bad) in refused) assertFalse(policy.accepts(bad), name)
    }

    // stretch

    @Test
    fun theStretchIsArgon2idOverTheUtf8OfTheNfcFormWithTheCostOfTheHeader() {
        val canonical = policy.canonical(Normalizer.normalize("pässwörd", Normalizer.Form.NFD).toCharArray())!!

        policy.stretch(canonical, header(memory = 131_072, passes = 4))

        val call = calls.single()
        assertEquals("70c3a4737377c3b67264", call.passwordAtCall.toHex())
        assertEquals(131_072, call.memory)
        assertEquals(4, call.passes)
        assertEquals(1, call.lanes)
        assertContentEquals(salt, call.salt)
    }

    @Test
    fun theStretchSeesTheSameBytesWhateverTheFormTheTextWasTypedIn() {
        val nfc = Normalizer.normalize("Привіт, café — 日本語", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD)

        val a = policy.stretch(policy.canonical(nfc.toCharArray())!!, header())
        val b = policy.stretch(policy.canonical(nfd.toCharArray())!!, header())

        assertContentEquals(a, b)
        assertContentEquals(nfc.toByteArray(Charsets.UTF_8), calls[0].passwordAtCall)
        assertContentEquals(nfc.toByteArray(Charsets.UTF_8), calls[1].passwordAtCall)
    }

    @Test
    fun theUtf8CopyOfThePasswordIsWipedAfterTheStretch() {
        policy.stretch("correct horse battery".toCharArray(), header())

        assertTrue(calls.single().password.all { it == 0.toByte() })
    }

    @Test
    fun theUtf8CopyIsWipedAlsoWhenTheStretchCouldNotBeMade() {
        val failing = DesktopPasswordPolicy { password, _, _, _, _ ->
            calls += Call(password, ByteArray(0), 0, 0, 0)
            throw OutOfMemoryError("simulated")
        }

        val failure = assertFailsWith<SecretStretchException> {
            failing.stretch("correct horse battery".toCharArray(), header())
        }

        assertIs<OutOfMemoryError>(failure.cause)
        assertTrue(calls.single().password.all { it == 0.toByte() })
    }

    @Test
    fun aHeaderThatIsNotAcceptedIsNeverStretched() {
        val password = "correct horse battery".toCharArray()
        val hostile = listOf(
            header(memory = 0xFFFF_FFFFL),
            // Narrowed to an Int this is 65 536: the Long is what is checked.
            header(memory = (1L shl 32) + 65_536),
            header(passes = 255),
            header(lanes = 255),
            header(version = 3),
            header(kdf = 1),
        )

        for (bad in hostile) assertFailsWith<IllegalArgumentException> { policy.stretch(password, bad) }

        assertTrue(calls.isEmpty())
    }

    @Test
    fun theDefaultPolicyRunsTheRealArgon2id() {
        val real = DesktopPasswordPolicy()
        val canonical = real.canonical("correct horse battery staple".toCharArray())!!

        val key = real.stretch(canonical, header())

        assertEquals("0d1a3c6523c8f06e4e0af9c515aa5b5448cfebd6838f2d52c3d8b6ef8ddc3c2e", key.toHex())
    }

    // factorInput

    @Test
    fun theFactorSignsThePrefixTheSaltAndTheUtf8OfTheNfcFormGolden() {
        val canonical = policy.canonical(Normalizer.normalize("pässwörd", Normalizer.Form.NFD).toCharArray())!!

        val input = policy.factorInput(canonical, salt)

        assertEquals(
            "656e636c792f7365637265742f68772f7634" + // "encly/secret/hw/v4"
                "000102030405060708090a0b0c0d0e0f" + // the salt
                "70c3a4737377c3b67264", // "pässwörd", NFC, UTF-8
            input.toHex(),
        )
    }

    @Test
    fun theFactorInputIsTheSameWhateverTheFormTheTextWasTypedIn() {
        val nfc = Normalizer.normalize("Привіт, café — 日本語", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD)

        assertContentEquals(
            policy.factorInput(policy.canonical(nfc.toCharArray())!!, salt),
            policy.factorInput(policy.canonical(nfd.toCharArray())!!, salt),
        )
    }

    @Test
    fun theStretchAndTheFactorAreMadeFromTheSameBytes() {
        val canonical = policy.canonical("Здоровенькі були 😀".toCharArray())!!

        val input = policy.factorInput(canonical, salt)
        policy.stretch(canonical, header())

        val prefix = "encly/secret/hw/v4".length + salt.size
        assertContentEquals(input.copyOfRange(prefix, input.size), calls.single().passwordAtCall)
    }

    @Test
    fun anEmptyPasswordHasOnlyThePrefixAndTheSaltInTheFactorInput() {
        assertEquals(
            "656e636c792f7365637265742f68772f7634000102030405060708090a0b0c0d0e0f",
            policy.factorInput(CharArray(0), salt).toHex(),
        )
    }

    // suggest

    @Test
    fun aSuggestionIsFiveWordsThatMayBeSet() {
        repeat(20) {
            val suggestion = assertNotNull(policy.suggest())

            assertEquals(5, String(suggestion).split(' ').size)
            assertEquals(SecretRule.OK, policy.mayBeSet(suggestion))
            assertContentEquals(suggestion, policy.canonical(suggestion))
        }
    }
}
