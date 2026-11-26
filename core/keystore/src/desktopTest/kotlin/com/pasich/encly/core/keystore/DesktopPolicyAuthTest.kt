package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.DuressPinChange
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.PinUnlock
import com.pasich.encly.core.security.RecoveryFollowUp
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.WipePinChange
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [AuthenticationManager] under the real [DesktopPasswordPolicy] and the real [DesktopPinFactor] (over an in-memory
 * secret store), with a cheap engine in place of Argon2id except where a test says it runs the real one. What these
 * tests check is the wiring of the policy into the manager: the slots written, the attempts counted, the erase
 * reusing the stretched half, the refund of a stretch that could not be made, the version 3 slot that is no slot.
 */
class DesktopPolicyAuthTest {
    private var engineCalls = 0
    private var failEngine = false
    private val policy = DesktopPasswordPolicy { password, salt, m, t, p ->
        engineCalls++
        if (failEngine) throw OutOfMemoryError("simulated")
        cheapEngine(password, salt, m, t, p)
    }
    private val fixture = PolicyFixture(policy)
    private val store = fixture.store
    private val auth = fixture.auth
    private val dek = ByteArray(32) { 0x3C }

    private fun set() = assertTrue(auth.configurePin(PASSWORD.toCharArray(), dek))

    private fun unlock(text: String) = auth.unlockWithPin(text.toCharArray())

    // The slots

    @Test
    fun aPasswordIsSetAsAVersion4SlotWithTheProductionCost() {
        set()

        val slot = store.getBytes("pin.slot")!!
        assertEquals(85, slot.size)
        assertContentEquals(byteArrayOf(4, 2, 1, 0, 1, 0, 0, 3, 1), slot.copyOf(9))
        listOf("pin.wipe.slot", "pin.decoy.slot").forEach {
            assertEquals(61, store.getBytes(it)!!.size, it)
            assertEquals(4, store.getBytes(it)!![0].toInt(), it)
        }
        assertEquals(3, store.getBytes("decoy.key")!![0].toInt())
        assertEquals(SecretKind.PASSWORD, auth.secretKind)
        assertTrue(auth.hasPinSlot())
    }

    @Test
    fun theSecretOpensTheVaultAndAWrongOneIsCounted() {
        set()

        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(PASSWORD)).dek)
        assertEquals(PinUnlock.WrongPin, unlock("not the password"))

        assertEquals(1, store.getInt("lockout.failures", 0))
        assertEquals(1, store.getInt("pin.strikes", 0))
    }

    @Test
    fun aPasswordSetInNfdOpensTheVaultWhenTypedInNfcAndTheOtherWayRound() {
        val nfc = Normalizer.normalize("Привіт, café — світе", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD)
        assertNotEquals(nfc, nfd)
        assertTrue(auth.configurePin(nfd.toCharArray(), dek))

        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(nfc)).dek)
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(nfd)).dek)
    }

    @Test
    fun aSecretThatMayNotBeSetIsRefusedEverywhereAndDerivesNothing() {
        set()
        engineCalls = 0

        for (weak in listOf("short", "123456789012", "aaaaaaaaaaaa", "x".repeat(129))) {
            assertFalse(auth.configurePin(weak.toCharArray(), dek), weak)
            assertEquals(WipePinChange.FAILED, auth.configureWipePin(weak.toCharArray()), weak)
            assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(weak.toCharArray(), dek) { true }, weak)
        }

        assertEquals(0, engineCalls)
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(PASSWORD)).dek)
    }

    @Test
    fun aTypedSecretOutsideTheUnlockBoundsIsACountedWrongGuessThatDerivesNothing() {
        set()
        engineCalls = 0

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(CharArray(0)))
        assertEquals(PinUnlock.WrongPin, unlock("a".repeat(257)))
        assertEquals(PinUnlock.WrongPin, unlock("lone \uD800 surrogate"))

        assertEquals(0, engineCalls)
        assertEquals(3, store.getInt("lockout.failures", 0))
    }

    @Test
    fun theUiAsksTheKindTheRuleAndASuggestion() {
        assertEquals(SecretKind.PASSWORD, auth.secretKind)
        assertEquals(768, auth.maxTypedUnits)
        assertEquals(SecretRule.OK, auth.checkNewSecret(PASSWORD.toCharArray()))
        assertEquals(SecretRule.TOO_SHORT, auth.checkNewSecret("short".toCharArray()))
        assertEquals(SecretRule.TOO_SIMPLE, auth.checkNewSecret("123456789012".toCharArray()))
        assertEquals(SecretRule.TOO_LONG, auth.checkNewSecret("ab".repeat(65).toCharArray()))
        assertEquals(SecretRule.INVALID, auth.checkNewSecret("a".repeat(300).toCharArray()))
        assertEquals(SecretRule.INVALID, auth.checkNewSecret("x\uDC00y".toCharArray()))
        val suggestion = assertNotNull(auth.suggestSecret())
        assertEquals(SecretRule.OK, auth.checkNewSecret(suggestion))
        assertTrue(auth.configurePin(suggestion, dek))
    }

    // A password that may be set can be unlocked from any equivalent typing

    /** 128 precomposed Hangul syllables with a final consonant: their NFD is 3 jamo each, 384 units in all. */
    private fun hangul(): Pair<String, String> {
        val nfc = StringBuilder()
        val nfd = StringBuilder()
        for (i in 0 until 128) {
            val lead = i % 19
            val vowel = (i * 3) % 21
            val tail = 1 + i % 27
            // Unicode chapter 3.12, the algorithmic mapping of Hangul syllables to conjoining jamo.
            nfc.append((0xAC00 + (lead * 21 + vowel) * 28 + tail).toChar())
            nfd.append((0x1100 + lead).toChar()).append((0x1161 + vowel).toChar()).append((0x11A7 + tail).toChar())
        }
        return nfc.toString() to nfd.toString()
    }

    /** 128 Vietnamese letters with two marks each (NFC and NFD of five letters by Python's unicodedata). */
    private fun vietnamese(): Pair<String, String> {
        val precomposed = listOf("\u1EC7", "\u1EB7", "\u1EE3", "\u1ED9", "\u1EE1")
        val decomposed = listOf("e\u0323\u0302", "a\u0323\u0306", "o\u031B\u0323", "o\u0323\u0302", "o\u031B\u0303")
        return (0 until 128).joinToString("") { precomposed[it % 5] } to
            (0 until 128).joinToString("") { decomposed[it % 5] }
    }

    @Test
    fun aHangulPasswordAtTheLongestThatMayBeSetIsNotAMissWhenTypedDecomposed() {
        val (nfc, nfd) = hangul()
        assertEquals(384, nfd.length)
        assertTrue(auth.configurePin(nfc.toCharArray(), dek))

        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(nfd)).dek)
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(nfc)).dek)
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aVietnamesePasswordAtTheLongestThatMayBeSetIsNotAMissWhenTypedDecomposed() {
        val (nfc, nfd) = vietnamese()
        assertEquals(384, nfd.length)
        assertEquals(SecretRule.OK, auth.checkNewSecret(nfd.toCharArray()))
        assertTrue(auth.configurePin(nfd.toCharArray(), dek))

        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(nfc)).dek)
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(nfd)).dek)
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aPasswordTooLongAfterNormalisingIsStillACountedMiss() {
        set()

        // 257 precomposed letters typed as 514 decomposed units: under the cap, but 257 units in NFC.
        assertEquals(PinUnlock.WrongPin, unlock("a\u0301".repeat(257)))
        // Over the cap of 768 units altogether.
        assertEquals(PinUnlock.WrongPin, unlock("a".repeat(769)))

        assertEquals(2, store.getInt("lockout.failures", 0))
    }

    // Change, wipe, duress, lockout

    @Test
    fun aChangedPasswordKeepsTheSaltSoTheWipeSecretKeepsWorking() {
        set()
        assertEquals(WipePinChange.SET, auth.configureWipePin(WIPE.toCharArray()))
        val salt = store.getBytes("pin.slot")!!.copyOfRange(9, 25)

        assertEquals(PinChange.SET, auth.changePin("another password here".toCharArray(), dek))

        assertContentEquals(salt, store.getBytes("pin.slot")!!.copyOfRange(9, 25))
        assertEquals(PinUnlock.WrongPin, unlock(PASSWORD))
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock("another password here")).dek)
        assertIs<PinUnlock.Erased>(unlock(WIPE))
    }

    @Test
    fun theWipeSecretErasesAndReusesTheStretchedHalfAndTheOtherKeySlot() {
        set()
        assertEquals(WipePinChange.SET, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(PinKeySlot.A.ordinal, store.getInt("pin.key_slot", -1))
        engineCalls = 0

        val erased = assertIs<PinUnlock.Erased>(unlock(WIPE))

        // One stretch for the whole attempt: the new slot is made from the half this attempt already computed.
        assertEquals(1, engineCalls)
        assertEquals(PinKeySlot.B.ordinal, store.getInt("pin.key_slot", -1))
        assertNotNull(fixture.secrets.peek(PinKeySlot.B))
        assertFalse(dek.contentEquals(erased.dek))
        val slot = store.getBytes("pin.slot")!!
        assertEquals(85, slot.size)
        assertEquals(4, slot[0].toInt())
        assertEquals(PinUnlock.WrongPin, unlock(PASSWORD))
        assertContentEquals(erased.dek, assertIs<PinUnlock.Success>(unlock(WIPE)).dek)
    }

    @Test
    fun theDuressSecretOpensTheDecoyVaultAndTheSecretStillOpensTheRealOne() {
        set()
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(DURESS.toCharArray(), dek) { true })

        val decoy = assertIs<PinUnlock.Decoy>(unlock(DURESS))

        assertFalse(dek.contentEquals(decoy.dek))
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(PASSWORD)).dek)
        assertIs<PinUnlock.Decoy>(unlock(DURESS))
    }

    @Test
    fun everyAttemptStretchesOnceWhateverItMatches() {
        set()
        assertEquals(WipePinChange.SET, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(DURESS.toCharArray(), dek) { true })

        val counts = listOf("not the password", DURESS, PASSWORD, WIPE).map {
            engineCalls = 0
            unlock(it)
            engineCalls
        }

        assertEquals(listOf(1, 1, 1, 1), counts)
    }

    @Test
    fun afterFiveMissesTheNextAttemptIsRefusedBeforeAnyStretchUntilTheLockoutEnds() {
        set()
        repeat(AuthenticationManager.MAX_ATTEMPTS) { assertEquals(PinUnlock.WrongPin, unlock("not the password")) }
        engineCalls = 0

        assertEquals(PinUnlock.LockedOut, unlock(PASSWORD))

        assertEquals(0, engineCalls)
        assertTrue(auth.remainingLockoutMillis() > 0L)
        fixture.clock.elapsed += AuthenticationManager.FIRST_LOCKOUT_MS + 1
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(PASSWORD)).dek)
        assertEquals(0L, auth.remainingLockoutMillis())
    }

    // A stretch that could not be made

    @Test
    fun anOutOfMemoryStretchOnUnlockIsGivenBackAndNeverCounted() {
        set()
        assertEquals(PinUnlock.WrongPin, unlock("not the password"))
        failEngine = true

        repeat(AuthenticationManager.MAX_ATTEMPTS + 2) {
            assertEquals(PinUnlock.KeyStoreFailed, unlock(PASSWORD))
        }

        assertEquals(1, store.getInt("lockout.failures", 0))
        assertEquals(1, store.getInt("pin.strikes", 0))
        assertEquals(0L, auth.remainingLockoutMillis())
        failEngine = false
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(PASSWORD)).dek)
    }

    @Test
    fun anOutOfMemoryStretchWhileSettingChangingAndMakingTheOtherSecretsIsAFailureNotACrash() {
        set()
        val before = store.getBytes("pin.slot")!!
        failEngine = true

        assertEquals(PinChange.FAILED, auth.changePin("another password here".toCharArray(), dek))
        assertEquals(WipePinChange.FAILED, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(DURESS.toCharArray(), dek) { true })
        assertFalse(auth.verifyPinAuth(PASSWORD.toCharArray()))

        assertContentEquals(before, store.getBytes("pin.slot")!!)
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    // A version 3 slot

    private fun storeVersion3Slot() {
        // 77 bytes starting with 3: well formed as far as the reader looks. No password can open it.
        store.edit {
            putBytes("pin.slot", ByteArray(77) { (it * 7).toByte() }.also { it[0] = 3 })
            putBytes("pin.wipe.slot", ByteArray(61) { 5 }.also { it[0] = 3 })
            putBytes("pin.decoy.slot", ByteArray(61) { 6 }.also { it[0] = 3 })
            putInt("auth.type", AuthType.PIN.ordinal)
        }
    }

    @Test
    fun aVersion3SlotIsNoSecretSlotOnTheDesktopAndNothingIsCountedOrDerivedForIt() {
        storeVersion3Slot()

        assertFalse(auth.hasPinSlot())
        assertEquals(AuthStrategy.RECOVERY_DATA, auth.isAuthStrategy())
        assertEquals(RecoveryFollowUp.MUST_SET_PIN, auth.recoveryFollowUp())
        assertEquals(PinUnlock.WrongPin, unlock(PASSWORD))
        assertFalse(auth.verifyPinAuth(PASSWORD.toCharArray()))

        assertEquals(0, engineCalls)
        assertEquals(0, store.getInt("lockout.failures", 0))
        assertEquals(0, fixture.secrets.handedOut.size)
    }

    @Test
    fun aNewSecretAfterTheRecoveryPhraseWritesAVersion4SlotAndLeavesNothingOfVersion3() {
        storeVersion3Slot()

        // What the recovery route does once the phrase has opened the vault (MUST_SET_PIN): set a new secret.
        assertEquals(PinChange.SET, auth.changePin(PASSWORD.toCharArray(), dek))

        assertEquals(4, store.getBytes("pin.slot")!![0].toInt())
        assertEquals(85, store.getBytes("pin.slot")!!.size)
        assertEquals(4, store.getBytes("pin.wipe.slot")!![0].toInt())
        assertEquals(4, store.getBytes("pin.decoy.slot")!![0].toInt())
        assertTrue(auth.hasPinSlot())
        assertEquals(AuthStrategy.PIN, auth.isAuthStrategy())
        assertEquals(RecoveryFollowUp.KEEP_OR_CHANGE, auth.recoveryFollowUp())
        assertContentEquals(dek, assertIs<PinUnlock.Success>(unlock(PASSWORD)).dek)
    }

    @Test
    fun aWipeSecretCanNotBeSetOnAVersion3SlotEither() {
        storeVersion3Slot()

        assertEquals(WipePinChange.FAILED, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(DURESS.toCharArray(), dek) { true })

        assertEquals(0, engineCalls)
    }

    // The real Argon2id

    @Test
    fun aSecretSetWithTheRealArgon2idOpensTheVaultAgain() {
        val real = PolicyFixture(DesktopPasswordPolicy())

        assertTrue(real.auth.configurePin(PASSWORD.toCharArray(), dek))

        assertContentEquals(dek, assertIs<PinUnlock.Success>(real.auth.unlockWithPin(PASSWORD.toCharArray())).dek)
        assertEquals(PinUnlock.WrongPin, real.auth.unlockWithPin("not the password".toCharArray()))
    }

    private companion object {
        const val PASSWORD = "correct horse battery"
        const val WIPE = "wipe everything now please"
        const val DURESS = "a duress password here"
    }
}
