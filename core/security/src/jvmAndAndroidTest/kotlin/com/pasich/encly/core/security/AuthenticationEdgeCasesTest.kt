package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.tempVaultStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The refusals and odd states around the PIN slots: PINs that may not share a slot, a
 * Keystore that fails mid-change, and store entries an older or interrupted build left behind.
 */
class AuthenticationEdgeCasesTest {
    private val store = tempVaultStore()
    private val factor = FakePinFactor()
    private val clock = FakeLockoutClock()
    private val auth = AuthenticationManager(store, factor, clock)
    private val dek = ByteArray(32) { 0x3C }

    @Before
    fun setUp() {
        assertTrue(auth.configurePin(PIN.toCharArray(), dek))
    }

    private fun duress(pin: String) = auth.configureDuressPin(pin.toCharArray(), dek) { true }

    private fun failures() = store.getInt("lockout.failures", 0)

    // --- one PIN, one slot --------------------------------------------------------------

    @Test
    fun theWipePinCanNotBeThePinAndTheTryCostsAMiss() {
        assertEquals(WipePinChange.SAME_AS_PIN, auth.configureWipePin(PIN.toCharArray()))

        assertEquals(1, failures())
    }

    @Test
    fun theWipePinCanNotBeTheDuressPin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, duress(DURESS))

        assertEquals(WipePinChange.IN_USE, auth.configureWipePin(DURESS.toCharArray()))
        assertEquals(1, failures())
    }

    @Test
    fun theDuressPinCanNotBeThePinAndTheTryCostsAMiss() {
        assertEquals(DuressPinChange.SAME_AS_PIN, duress(PIN))

        assertEquals(1, failures())
    }

    @Test
    fun theDuressPinCanNotBeTheWipePin() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(WIPE.toCharArray()))

        assertEquals(DuressPinChange.IN_USE, duress(WIPE))
        assertEquals(1, failures())
    }

    @Test
    fun theVaultPinCanNotBecomeTheDuressPin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, duress(DURESS))

        assertEquals(PinChange.IN_USE, auth.changePin(DURESS.toCharArray(), dek))
        assertEquals(1, failures())
        assertTrue("the old PIN still works", auth.verifyPinAuth(PIN.toCharArray()))
    }

    @Test
    fun changingThePinToTheWipePinTurnsTheWipePinOffInsteadOfKeepingTwoSlots() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(WIPE.toCharArray()))

        assertEquals(PinChange.SET, auth.changePin(WIPE.toCharArray(), dek))

        val unlock = auth.unlockWithPin(WIPE.toCharArray())
        assertTrue("the PIN opens the vault rather than erasing it", unlock is PinUnlock.Success)
        (unlock as PinUnlock.Success).dek.fill(0)
    }

    // --- refusals before any key is touched -----------------------------------------------

    @Test
    fun malformedPinsAreRefusedEverywhere() {
        listOf("12345", "1234567", "12345a", "").forEach { bad ->
            assertEquals(PinChange.FAILED, auth.changePin(bad.toCharArray(), dek))
            assertEquals(WipePinChange.FAILED, auth.configureWipePin(bad.toCharArray()))
            assertEquals(DuressPinChange.FAILED, duress(bad))
        }
        assertEquals(0, failures())
    }

    @Test
    fun aKeyOfTheWrongSizeIsRefused() {
        assertEquals(PinChange.FAILED, auth.changePin(NEW_PIN.toCharArray(), ByteArray(31)))
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(DURESS.toCharArray(), ByteArray(16)) { true })
    }

    @Test
    fun nothingIsConfiguredWithoutAPinSlot() {
        val empty = AuthenticationManager(tempVaultStore(), FakePinFactor(), FakeLockoutClock())

        assertEquals(WipePinChange.FAILED, empty.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.FAILED, empty.configureDuressPin(DURESS.toCharArray(), dek) { true })
    }

    @Test
    fun wipeAndDuressPinsAreRefusedDuringALockout() {
        repeat(LOCKOUT_AFTER) { auth.unlockWithPin(WRONG.toCharArray()) }
        assertTrue(auth.remainingLockoutMillis() > 0)

        assertEquals(WipePinChange.FAILED, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.FAILED, duress(DURESS))
    }

    @Test
    fun aKeystoreFailureWhileSettingTheWipeOrDuressPinChangesNothing() {
        val before = store.keys().associateWith { store.getBytes(it)!!.toList() }
        factor.failing = true

        assertEquals(WipePinChange.FAILED, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.FAILED, duress(DURESS))
        assertEquals(PinChange.FAILED, auth.changePin(NEW_PIN.toCharArray(), dek))

        assertEquals(before, store.keys().associateWith { store.getBytes(it)!!.toList() })
    }

    @Test
    fun aCorruptPinSlotRefusesTheWipeAndDuressPins() {
        store.edit { putBytes("pin.slot", ByteArray(10)) }

        assertEquals(WipePinChange.FAILED, auth.configureWipePin(WIPE.toCharArray()))
        assertEquals(DuressPinChange.FAILED, duress(DURESS))
    }

    @Test
    fun aStoreThatCanNotBeWrittenRefusesTheNewWipePinAndPinChange() {
        val failingStore = tempVaultStore()
        val manager = AuthenticationManager(failingStore, FakePinFactor(), FakeLockoutClock())
        assertTrue(manager.configurePin(PIN.toCharArray(), dek))
        failingStore.beforeRename = { throw java.io.IOException("disk full") }

        assertEquals(WipePinChange.FAILED, manager.configureWipePin(WIPE.toCharArray()))
        assertEquals(PinChange.FAILED, manager.changePin(NEW_PIN.toCharArray(), dek))
    }

    // --- the wipe's progress marker and the retired key -------------------------------------

    @Test
    fun withoutAMarkerNoWipeIsPending() {
        assertNull(auth.pendingWipe())
    }

    @Test
    fun anUnknownMarkerRedoesTheWholeErase() {
        store.edit { putInt("wipe.pending", 99) }

        assertEquals(WipeStage.DATABASE, auth.pendingWipe())
    }

    @Test
    fun theMarkerAdvancesToCleanupAndIsThenCleared() {
        store.edit { putInt("wipe.pending", WipeStage.DATABASE.ordinal) }
        assertEquals(WipeStage.DATABASE, auth.pendingWipe())

        assertTrue(auth.markWipeDatabaseCreated())
        assertEquals(WipeStage.CLEANUP, auth.pendingWipe())

        assertTrue(auth.clearPendingWipe())
        assertNull(auth.pendingWipe())
    }

    @Test
    fun aStrayKeyInSlotBIsDeletedWhenNothingUsesIt() {
        factor.ensureKey(PinKeySlot.B)

        auth.deleteStrayPinKey()

        assertFalse(factor.hasKey(PinKeySlot.B))
        assertTrue("the key every older build knows is never touched", factor.hasKey(PinKeySlot.A))
    }

    @Test
    fun theKeyInSlotBIsKeptWhileAnEraseIsPending() {
        factor.ensureKey(PinKeySlot.B)
        store.edit { putInt("wipe.pending", WipeStage.DATABASE.ordinal) }

        auth.deleteStrayPinKey()

        assertTrue(factor.hasKey(PinKeySlot.B))
    }

    @Test
    fun theKeyInSlotBIsKeptWhenSlotBIsTheActiveOne() {
        factor.ensureKey(PinKeySlot.B)
        store.edit { putInt("pin.key_slot", PinKeySlot.B.ordinal) }

        auth.deleteStrayPinKey()
        assertTrue(factor.hasKey(PinKeySlot.B))

        auth.deleteRetiredPinKey()
        assertFalse("the retired key is the one in the other slot", factor.hasKey(PinKeySlot.A))
        assertTrue(factor.hasKey(PinKeySlot.B))
    }

    @Test
    fun anUnknownKeySlotValueFallsBackToSlotA() {
        store.edit { putInt("pin.key_slot", 7) }

        val unlock = auth.unlockWithPin(PIN.toCharArray())

        assertTrue(unlock is PinUnlock.Success)
        (unlock as PinUnlock.Success).dek.fill(0)
    }

    private companion object {
        const val PIN = "135790"
        const val NEW_PIN = "246813"
        const val WIPE = "864200"
        const val DURESS = "975310"
        const val WRONG = "000111"
        const val LOCKOUT_AFTER = 5
    }
}
