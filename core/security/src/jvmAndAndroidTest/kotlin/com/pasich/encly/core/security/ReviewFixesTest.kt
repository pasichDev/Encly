package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.FakeV4PasswordPolicy
import com.pasich.encly.testutil.tempVaultStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two review findings: a failed stretch after the device key was replaced must not leave the wipe and duress slots
 * looking alive, and the decoy session's refusal of a new secret must cost what the real vault's own refusal costs.
 */
class ReviewFixesTest {
    private val dek = ByteArray(32) { 0x3C }

    private class Vault(val policy: UnlockSecretPolicy) {
        val store = tempVaultStore()
        val factor = FakePinFactor()
        val auth = AuthenticationManager(store, factor, FakeLockoutClock(), policy)
    }

    private fun passwordVault(): Pair<Vault, FakeV4PasswordPolicy> {
        val policy = FakeV4PasswordPolicy()
        val vault = Vault(policy)
        assertTrue(vault.auth.configurePin(PASSWORD.toCharArray(), dek))
        assertEquals(WipePinChange.SET, vault.auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, vault.auth.configureDuressPin(DURESS.toCharArray(), dek) { true })
        return vault to policy
    }

    private fun snapshot(store: VaultStore): Map<String, List<Byte>?> =
        store.keys().associateWith { store.getBytes(it)?.toList() }

    // --- a failed stretch after the device key was replaced ---------------------------------------

    @Test
    fun aMissingKeyMadeAnewBeforeAFailedStretchTurnsTheWipeAndDuressSecretsOffAndSaysSo() {
        val (vault, policy) = passwordVault()
        vault.factor.delete(PinKeySlot.A)
        policy.failStretch = true

        assertEquals(PinChange.FAILED, vault.auth.changePin("another password here".toCharArray(), dek))

        assertTrue(vault.auth.wipePinTurnedOff())
        assertTrue(vault.auth.duressPinTurnedOff(dek))
        assertTrue(vault.auth.hasDecoyVault(dek))
        assertEquals(61, vault.store.getBytes("pin.wipe.slot")!!.size)
        assertEquals(4, vault.store.getBytes("pin.wipe.slot")!![0].toInt())
        // The retry finds the new key in place: the notice stays, and the dead slots do not come back to life.
        policy.failStretch = false
        assertEquals(PinChange.SET, vault.auth.changePin("another password here".toCharArray(), dek))
        assertTrue(vault.auth.wipePinTurnedOff())
        assertTrue(vault.auth.duressPinTurnedOff(dek))
        assertEquals(PinUnlock.WrongPin, vault.auth.unlockWithPin(WIPE.toCharArray()))
        assertEquals(PinUnlock.WrongPin, vault.auth.unlockWithPin(DURESS.toCharArray()))
    }

    @Test
    fun aLostKeyResetBeforeAFailedStretchDoesTheSame() {
        val (vault, policy) = passwordVault()
        vault.factor.lost = true
        policy.failStretch = true

        assertEquals(PinChange.FAILED, vault.auth.changePin("another password here".toCharArray(), dek))

        assertTrue(vault.auth.wipePinTurnedOff())
        assertTrue(vault.auth.duressPinTurnedOff(dek))
    }

    @Test
    fun aFailedStretchWithTheKeyInPlaceChangesNothing() {
        val (vault, policy) = passwordVault()
        val before = snapshot(vault.store)
        policy.failStretch = true

        assertEquals(PinChange.FAILED, vault.auth.changePin("another password here".toCharArray(), dek))

        assertEquals(before, snapshot(vault.store))
        assertFalse(vault.auth.wipePinTurnedOff())
    }

    @Test
    fun aFailedStretchOfTheFirstSecretWritesNothing() {
        val policy = FakeV4PasswordPolicy().apply { failStretch = true }
        val vault = Vault(policy)

        assertEquals(PinChange.FAILED, vault.auth.changePin(PASSWORD.toCharArray(), dek))

        assertTrue(vault.store.keys().none { it.startsWith("pin.") || it.startsWith("decoy.") })
    }

    @Test
    fun underThePinPolicyATransientFactorFailureAfterAKeyWasMadeIsExactlyWhatItWasAndNothingIsTurnedOff() {
        val vault = Vault(PinSecretPolicy())
        assertTrue(vault.auth.configurePin("482913".toCharArray(), dek))
        assertEquals(WipePinChange.SET, vault.auth.configureWipePin("135790".toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, vault.auth.configureDuressPin("246802".toCharArray(), dek) { true })
        vault.factor.delete(PinKeySlot.A)
        vault.factor.failing = true
        val before = snapshot(vault.store)

        assertEquals(PinChange.FAILED, vault.auth.changePin("975318".toCharArray(), dek))

        // As on main: the key was made, the failure forgot it, and not one entry of the store moved.
        assertEquals(before, snapshot(vault.store))
        assertFalse(vault.auth.wipePinTurnedOff())
        assertTrue(vault.factor.hasKey(PinKeySlot.A))
    }

    // --- the decoy session's refusal costs what the real vault's does ------------------------------

    @Test
    fun underThePasswordPolicyADecoyRefusalTakesTheRealDecisionForEveryInputClass() {
        val (vault, policy) = passwordVault()
        val inputs = mapOf(
            "malformed (empty)" to "",
            "malformed (over the unlock length)" to "a".repeat(300),
            "not settable (too short)" to "short",
            "not settable (digits)" to "123456789012",
            "settable" to "another password here",
        )

        for ((name, input) in inputs) {
            policy.resetCounts()
            vault.auth.changePin(input.toCharArray(), dek)
            val real = policy.stretchCalls to policy.factorInputCalls
            policy.resetCounts()
            vault.auth.spendPinCost(input.toCharArray())
            val decoy = policy.stretchCalls to policy.factorInputCalls

            assertEquals(name, real, decoy)
        }
        policy.resetCounts()
        vault.auth.spendPinCost("short".toCharArray())
        assertEquals(0, policy.stretchCalls)
        vault.auth.spendPinCost("another password here".toCharArray())
        assertEquals(1, policy.stretchCalls)
    }

    @Test
    fun underThePinPolicyADecoyRefusalCostsWhatItAlwaysDid() {
        val vault = Vault(PinSecretPolicy())
        assertTrue(vault.auth.configurePin("482913".toCharArray(), dek))

        for ((input, macs) in listOf("" to 0, "12345" to 0, "12a456" to 0, "1234567890123" to 0, "654321" to 1)) {
            val before = vault.factor.calls
            vault.auth.spendPinCost(input.toCharArray())
            assertEquals(input, macs, vault.factor.calls - before)
        }
    }

    @Test
    fun theSecretOfTheDecoyRefusalIsNotKeptAnywhere() {
        val (vault, policy) = passwordVault()
        val typed = "another password here".toCharArray()
        policy.canonicals.clear()

        vault.auth.spendPinCost(typed)

        assertArrayEquals("another password here".toCharArray(), typed)
        policy.canonicals.forEach { copy -> assertTrue(copy.all { it == 0.toChar() }) }
    }

    private companion object {
        const val PASSWORD = "correct horse battery"
        const val WIPE = "wipe everything now please"
        const val DURESS = "a duress password here"
    }
}
