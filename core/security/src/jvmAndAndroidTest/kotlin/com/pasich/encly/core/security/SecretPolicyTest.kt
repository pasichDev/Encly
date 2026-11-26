package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.FakeV4PasswordPolicy
import com.pasich.encly.testutil.tempVaultStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AuthenticationManager] under a policy of slot version 4 ([FakeV4PasswordPolicy], a test double with a cheap
 * stretch), and under the PIN policy facing slots it must not touch: the version dispatch, the acceptance of a slot's
 * costs before any work is done for it, the wiping of every secret, and the shape of the store.
 */
class SecretPolicyTest {
    private val store = tempVaultStore()
    private val factor = FakePinFactor()
    private val clock = FakeLockoutClock()
    private val policy = FakeV4PasswordPolicy()
    private val auth = AuthenticationManager(store, factor, clock, policy)
    private val dek = ByteArray(32) { 0x3C }

    @Before
    fun setUp() {
        assertTrue(auth.configurePin(secret(PASSWORD), dek))
    }

    // --- Slots written under a version 4 policy ---------------------------------------------------

    @Test
    fun theSlotsAreVersion4AndTheDecoyKeyStaysVersion3() {
        val main = store.getBytes("pin.slot")!!

        assertEquals(85, main.size)
        assertEquals(4, main[0].toInt())
        // Argon2id, the OS-store factor, 65 536 KiB, 3 passes, 1 lane.
        assertArrayEquals(byteArrayOf(4, 2, 1, 0, 1, 0, 0, 3, 1), main.copyOf(9))
        listOf("pin.wipe.slot", "pin.decoy.slot").forEach {
            assertEquals(it, 61, store.getBytes(it)!!.size)
            assertEquals(it, 4, store.getBytes(it)!![0].toInt())
        }
        assertEquals(61, store.getBytes("decoy.key")!!.size)
        assertEquals(3, store.getBytes("decoy.key")!![0].toInt())
    }

    @Test
    fun theSecretOpensTheVaultAndAWrongOneIsCounted() {
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret("not the password")))

        assertEquals(1, store.getInt("lockout.failures", 0))
    }

    @Test
    fun theWipeSecretErasesAndTheNewVaultKeepsTheVersion4Format() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(secret(WIPE)))

        val erased = auth.unlockWithPin(secret(WIPE)) as PinUnlock.Erased

        assertFalse(dek.contentEquals(erased.dek))
        val slot = store.getBytes("pin.slot")!!
        assertEquals(85, slot.size)
        assertEquals(4, slot[0].toInt())
        assertEquals(61, store.getBytes("pin.wipe.slot")!!.size)
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret(PASSWORD)))
        // The erase reused the stretched half: its slot is opened by the wipe secret, which is the new vault's secret.
        assertArrayEquals(erased.dek, (auth.unlockWithPin(secret(WIPE)) as PinUnlock.Success).dek)
    }

    @Test
    fun theDuressSecretOpensTheDecoyVault() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(secret(DURESS), dek) { true })

        val decoy = auth.unlockWithPin(secret(DURESS)) as PinUnlock.Decoy

        assertFalse(dek.contentEquals(decoy.dek))
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)
    }

    @Test
    fun oneAttemptDoesTheSameWorkWhicheverSlotMatches() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(secret(WIPE)))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(secret(DURESS), dek) { true })
        val stretches = mutableListOf<Int>()
        val factorInputs = mutableListOf<Int>()

        // A wrong secret, the duress secret, the secret: one stretch and one device-factor input each.
        for (typed in listOf("not the password", DURESS, PASSWORD)) {
            policy.resetCounts()
            auth.unlockWithPin(secret(typed))
            stretches += policy.stretchCalls
            factorInputs += policy.factorInputCalls
        }
        // The wipe secret: the erase reuses the stretched half, so one stretch too (and the new key's factor input).
        policy.resetCounts()
        assertTrue(auth.unlockWithPin(secret(WIPE)) is PinUnlock.Erased)
        stretches += policy.stretchCalls

        assertEquals(listOf(1, 1, 1, 1), stretches)
        assertEquals(listOf(1, 1, 1), factorInputs)
    }

    @Test
    fun theStoreHasTheSameKeysSizesAndVersionsWithAndWithoutAWipeAndADuressSecret() {
        val plainStore = store
        val fullStore = tempVaultStore()
        val full = AuthenticationManager(fullStore, FakePinFactor(), FakeLockoutClock(), FakeV4PasswordPolicy())
        assertTrue(full.configurePin(secret(PASSWORD), dek))
        assertEquals(WipePinChange.SET, full.configureWipePin(secret(WIPE)))
        assertEquals(DuressPinChange.SET_NEW_VAULT, full.configureDuressPin(secret(DURESS), dek) { true })

        assertEquals(shape(plainStore), shape(fullStore))
    }

    @Test
    fun aSecretThatMayNotBeSetIsRefusedEverywhere() {
        // Too short, and digits only: the policy's set rule, not a rule of the manager.
        listOf("short", "123456789012").forEach { weak ->
            assertFalse(auth.configurePin(secret(weak), dek))
            assertEquals(WipePinChange.FAILED, auth.configureWipePin(secret(weak)))
            assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(secret(weak), dek) { true })
        }
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)
    }

    @Test
    fun aSecretThatCanNotBeTypedIsAWrongGuessThatIsCounted() {
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(CharArray(0)))

        assertEquals(1, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aKeptSlotWithOtherCostsTurnsTheWipeSecretOffAndSaysSo() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(secret(WIPE)))
        // The policy now writes 4 passes (still acceptable): the new slot is another format, so the wipe slot
        // (sealed under keys and an AAD of the old header) could never open again.
        policy.passes = 4

        assertTrue(auth.configurePin(secret("another password entirely"), dek))

        assertTrue(auth.wipePinTurnedOff())
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret(WIPE)))
        assertEquals(85, store.getBytes("pin.slot")!!.size)
        assertEquals(4, store.getBytes("pin.slot")!![7].toInt())
    }

    @Test
    fun aKeptSlotWithTheSameCostsKeepsTheWipeSecret() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(secret(WIPE)))

        assertTrue(auth.configurePin(secret("another password entirely"), dek))

        assertFalse(auth.wipePinTurnedOff())
        assertTrue(auth.unlockWithPin(secret(WIPE)) is PinUnlock.Erased)
    }

    // --- Slots a policy does not accept: no slot, no count, no work -------------------------------

    @Test
    fun costsInsideTheLimitsAreAcceptedAndOutsideTheyAreNoSlotAtAll() {
        val cases: List<Triple<String, FakeV4PasswordPolicy.() -> Unit, Boolean>> = listOf(
            Triple("memory 65535", { memoryKiB = 65_535L }, false),
            Triple("memory 65536", { memoryKiB = 65_536L }, true),
            Triple("memory 1048576", { memoryKiB = 1_048_576L }, true),
            Triple("memory 1048577", { memoryKiB = 1_048_577L }, false),
            Triple("memory 4294967295", { memoryKiB = 0xFFFF_FFFFL }, false),
            Triple("memory 0", { memoryKiB = 0L }, false),
            Triple("passes 2", { passes = 2 }, false),
            Triple("passes 3", { passes = 3 }, true),
            Triple("passes 10", { passes = 10 }, true),
            Triple("passes 11", { passes = 11 }, false),
            Triple("passes 255", { passes = 255 }, false),
            Triple("lanes 0", { lanes = 0 }, false),
            Triple("lanes 1", { lanes = 1 }, true),
            Triple("lanes 2", { lanes = 2 }, false),
            Triple("lanes 255", { lanes = 255 }, false),
            Triple("kdf PBKDF2", { kdfId = SlotHeader.KDF_PBKDF2 }, false),
            Triple("kdf 3", { kdfId = 3 }, false),
            Triple("kdf 255", { kdfId = 255 }, false),
            Triple("factor 0", { factorId = 0 }, false),
            Triple("factor 2", { factorId = 2 }, false),
        )

        cases.forEach { (name, change, accepted) ->
            val written = FakeV4PasswordPolicy().apply(change)
            val hostStore = tempVaultStore()
            val hostFactor = FakePinFactor()
            val host = AuthenticationManager(hostStore, hostFactor, FakeLockoutClock(), written)
            assertTrue(name, host.configurePin(secret(PASSWORD), dek))
            written.resetCounts()
            val macsBefore = hostFactor.calls

            val result = host.unlockWithPin(secret(PASSWORD))

            if (accepted) {
                assertTrue(name, result is PinUnlock.Success)
            } else {
                assertEquals(name, PinUnlock.WrongPin, result)
                assertEquals(name, 0, written.stretchCalls)
                assertEquals(name, 0, written.factorInputCalls)
                assertEquals(name, macsBefore, hostFactor.calls)
                assertEquals(name, 0, hostStore.getInt("lockout.failures", 0))
                assertEquals(name, 0, hostStore.getInt("pin.strikes", 0))
                assertEquals(name, 0L, host.remainingLockoutMillis())
            }
        }
    }

    @Test
    fun aSlotThatIsNotAcceptedIsNotUsedToSetAWipeOrADuressSecretEither() {
        policy.memoryKiB = 0xFFFF_FFFFL
        assertTrue(auth.configurePin(secret(PASSWORD), dek))
        policy.resetCounts()
        val macs = factor.calls

        assertEquals(WipePinChange.FAILED, auth.configureWipePin(secret(WIPE)))
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(secret(DURESS), dek) { true })

        assertEquals(0, policy.stretchCalls)
        assertEquals(macs, factor.calls)
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aSlotThatIsNotAcceptedIsReplacedByAFreshOneOnANewSecret() {
        policy.memoryKiB = 0xFFFF_FFFFL
        assertTrue(auth.configurePin(secret(PASSWORD), dek))
        val hostile = store.getBytes("pin.slot")!!
        policy.memoryKiB = 65_536L

        assertTrue(auth.configurePin(secret(PASSWORD), dek))

        val fresh = store.getBytes("pin.slot")!!
        assertNotEquals(hostile.copyOfRange(9, 25).toList(), fresh.copyOfRange(9, 25).toList())
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)
    }

    @Test
    fun aVersion3SlotIsNoSlotUnderAVersion4Policy() {
        store.edit { putBytes("pin.slot", GoldenVectors.chainPinSlot) }
        policy.resetCounts()
        val macs = factor.calls

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret(PASSWORD)))
        assertFalse(auth.verifyPinAuth(secret(PASSWORD)))

        assertEquals(0, policy.stretchCalls)
        assertEquals(macs, factor.calls)
        assertEquals(0, store.getInt("lockout.failures", 0))
        assertEquals(0, store.getInt("pin.strikes", 0))
    }

    @Test
    fun aVersion4SlotIsJustAnUnparsableSlotUnderThePinPolicyACountedMissNeverDerived() {
        val pinAuth = AuthenticationManager(store, factor, clock)
        val macs = factor.calls

        repeat(AuthenticationManager.MAX_ATTEMPTS) {
            assertEquals(PinUnlock.WrongPin, pinAuth.unlockWithPin(secret("123456")))
        }
        assertEquals(PinUnlock.LockedOut, pinAuth.unlockWithPin(secret("123456")))
        assertEquals(WipePinChange.FAILED, pinAuth.configureWipePin(secret("654321")))
        assertEquals(DuressPinChange.FAILED, pinAuth.configureDuressPin(secret("654321"), dek) { true })

        assertEquals(macs, factor.calls)
        assertEquals(AuthenticationManager.MAX_ATTEMPTS, store.getInt("lockout.failures", 0))
        assertTrue(pinAuth.remainingLockoutMillis() > 0L)
    }

    @Test
    fun aDamagedVersion4SlotIsNotCountedAndADamagedVersion3SlotKeepsCountingForThePin() {
        store.edit { putBytes("pin.slot", store.getBytes("pin.slot")!!.copyOf(84)) }
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret(PASSWORD)))
        assertEquals(0, store.getInt("lockout.failures", 0))

        // Today's behaviour of the PIN policy, kept: a damaged version 3 slot is a counted miss.
        val pinStore = tempVaultStore()
        val pinAuth = AuthenticationManager(pinStore, FakePinFactor(), FakeLockoutClock())
        pinStore.edit { putBytes("pin.slot", GoldenVectors.chainPinSlot.copyOf(76)) }
        assertEquals(PinUnlock.WrongPin, pinAuth.unlockWithPin(secret("123456")))
        assertEquals(1, pinStore.getInt("lockout.failures", 0))
    }

    // --- Wiping ------------------------------------------------------------------------------------

    @Test
    fun everyCanonicalCopyIsWipedAndTheCallersSecretIsLeftAlone() {
        val typed = secret(PASSWORD)
        val wipe = secret(WIPE)
        val duress = secret(DURESS)
        assertEquals(WipePinChange.SET, auth.configureWipePin(wipe))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(duress, dek) { true })

        auth.unlockWithPin(typed)
        auth.unlockWithPin(duress)
        auth.verifyPinAuth(typed)
        auth.unlockWithPin(secret("not the password"))
        auth.configurePin(typed, dek)
        auth.unlockWithPin(wipe)
        auth.spendPinCost(typed)

        assertTrue(policy.canonicals.size >= 10)
        policy.canonicals.forEach { copy -> assertTrue(copy.all { it == 0.toChar() }) }
        assertArrayEquals(secret(PASSWORD), typed)
        assertArrayEquals(secret(WIPE), wipe)
        assertArrayEquals(secret(DURESS), duress)
    }

    @Test
    fun theCanonicalCopiesAreWipedWhenTheDeviceFactorFails() {
        val typed = secret(PASSWORD)
        val failures = listOf<() -> Unit>(
            { factor.failing = true },
            { factor.lost = true },
            { factor.unavailable = true },
        )
        for (failure in failures) {
            failure()
            policy.canonicals.clear()

            assertFalse(auth.unlockWithPin(typed) is PinUnlock.Success)
            auth.configurePin(typed, dek)
            auth.spendPinCost(typed)

            assertTrue(policy.canonicals.isNotEmpty())
            policy.canonicals.forEach { copy -> assertTrue(copy.all { it == 0.toChar() }) }
            assertArrayEquals(secret(PASSWORD), typed)
            factor.failing = false
            factor.lost = false
            factor.unavailable = false
            factor.ensureKey(PinKeySlot.A)
        }
    }

    @Test
    fun thePinPolicyWipesItsCopiesToo() {
        val canonicals = mutableListOf<CharArray>()
        val recording = object : UnlockSecretPolicy by PinSecretPolicy() {
            override fun canonical(typed: CharArray): CharArray? =
                PinSecretPolicy().canonical(typed)?.also { canonicals += it }

            // Delegation hands the default over to the delegate: the recording policy answers for setting too.
            override fun canonicalForSetting(typed: CharArray): CharArray? = canonical(typed)
        }
        val pinAuth = AuthenticationManager(tempVaultStore(), FakePinFactor(), FakeLockoutClock(), recording)
        val pin = "135790".toCharArray()
        val wipe = "864200".toCharArray()

        assertTrue(pinAuth.configurePin(pin, dek))
        assertEquals(WipePinChange.SET, pinAuth.configureWipePin(wipe))
        assertTrue(pinAuth.unlockWithPin(pin) is PinUnlock.Success)
        assertTrue(pinAuth.unlockWithPin(wipe) is PinUnlock.Erased)
        assertEquals(PinUnlock.WrongPin, pinAuth.unlockWithPin("12ab".toCharArray()))

        assertEquals(4, canonicals.size)
        canonicals.forEach { copy -> assertTrue(copy.all { it == 0.toChar() }) }
        assertArrayEquals("135790".toCharArray(), pin)
        assertArrayEquals("864200".toCharArray(), wipe)
    }

    private fun secret(value: String) = value.toCharArray()

    /** Every key of the store with the length and the first byte of its value: what an observer of the file sees. */
    private fun shape(store: VaultStore): Map<String, Pair<Int, Int>> = store.keys().associateWith { key ->
        val bytes = store.getBytes(key)!!
        bytes.size to (if (bytes.isEmpty()) -1 else bytes[0].toInt())
    }.filterKeys { it.startsWith("pin.") || it.startsWith("decoy.") }

    private companion object {
        const val PASSWORD = "correct horse battery"
        const val WIPE = "wipe everything now please"
        const val DURESS = "a duress password here"
    }
}
