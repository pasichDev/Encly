package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** The v3 PIN slot: the KEK needs both the PIN and the device-bound factor. */
class AuthenticationManagerTest {

    private lateinit var file: File
    private lateinit var store: VaultStore
    private lateinit var factor: FakePinFactor
    private lateinit var manager: AuthenticationManager

    @Before
    fun setUp() {
        file = tempVaultFile()
        store = VaultStore(file)
        factor = FakePinFactor()
        manager = AuthenticationManager(store, factor, FakeLockoutClock())
    }

    @Test
    fun correctPinUnwrapsTheConfiguredDek() {
        val dek = ByteArray(32) { 0x5A }
        assertTrue(manager.configurePin(pin("123456"), dek))

        val unlocked = manager.unlockWithPin(pin("123456"))

        assertTrue(unlocked is PinUnlock.Success)
        assertArrayEquals(dek, (unlocked as PinUnlock.Success).dek)
        assertTrue(manager.hasPinSlot())
        assertTrue(manager.verifyPinAuth(pin("123456")))
    }

    @Test
    fun wrongPinNeverUnwrapsTheDek() {
        val dek = ByteArray(32) { 0x2A }
        assertTrue(manager.configurePin(pin("123456"), dek))

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin(pin("654321")))
        assertEquals(0L, manager.remainingLockoutMillis())
        assertTrue(manager.unlockWithPin(pin("123456")) is PinUnlock.Success)
    }

    @Test
    fun theSlotIsUselessWithoutTheDeviceFactor() {
        // A copied slot on another device (another hardware key) cannot be opened even with
        // the right PIN: the attacker has no way to compute the hardware half of the KEK.
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        val otherDevice = AuthenticationManager(VaultStore(file), FakePinFactor().apply { reset() }, FakeLockoutClock())

        assertEquals(PinUnlock.WrongPin, otherDevice.unlockWithPin(pin("123456")))
    }

    @Test
    fun everyGuessRunsThroughTheDeviceFactor() {
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        val before = factor.calls

        repeat(3) { manager.unlockWithPin(pin("00000$it")) }

        assertEquals(before + 3, factor.calls)
    }

    @Test
    fun aLostDeviceKeyIsReportedAndNotCountedAsAGuess() {
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        factor.lost = true

        repeat(AuthenticationManager.MAX_ATTEMPTS + 2) {
            assertEquals(PinUnlock.KeyLost, manager.unlockWithPin(pin("123456")))
        }
        assertEquals(0L, manager.remainingLockoutMillis())
    }

    @Test
    fun aTransientFactorErrorFailsWithoutCounting() {
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        factor.failing = true

        repeat(AuthenticationManager.MAX_ATTEMPTS + 2) {
            assertEquals(PinUnlock.Failed, manager.unlockWithPin(pin("123456")))
        }
        factor.failing = false
        assertTrue(manager.unlockWithPin(pin("123456")) is PinUnlock.Success)
    }

    @Test
    fun aNewPinAfterKeyLossGetsAFreshKey() {
        // Unlocked with the recovery phrase after the Keystore key was invalidated.
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        factor.lost = true

        assertTrue(manager.configurePin(pin("654321"), ByteArray(32) { 7 }))

        assertTrue(manager.unlockWithPin(pin("654321")) is PinUnlock.Success)
    }

    @Test
    fun aFactorThatCannotBeCreatedRefusesThePin() {
        factor.failing = true
        assertFalse(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        assertFalse(manager.hasPinSlot())
    }

    @Test
    fun tamperedPinSlotFailsClosed() {
        val dek = ByteArray(32) { 0x33 }
        assertTrue(manager.configurePin(pin("123456"), dek))
        val slot = store.getBytes("pin.slot")!!
        slot[slot.size - 1] = (slot[slot.size - 1].toInt() xor 1).toByte()
        store.edit { putBytes("pin.slot", slot) }

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin(pin("123456")))
    }

    @Test
    fun aSlotOfAnotherVersionIsRefused() {
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))
        val slot = store.getBytes("pin.slot")!!
        slot[0] = 2
        store.edit { putBytes("pin.slot", slot) }

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin(pin("123456")))
    }

    @Test
    fun eachPinSlotUsesAFreshSaltAndIv() {
        val dek = ByteArray(32) { 1 }
        assertTrue(manager.configurePin(pin("123456"), dek))
        val first = store.getBytes("pin.slot")!!
        assertTrue(manager.configurePin(pin("123456"), dek))

        assertNotEquals(first.toList(), store.getBytes("pin.slot")!!.toList())
    }

    @Test
    fun theSlotSurvivesAReload() {
        val dek = ByteArray(32) { 9 }
        assertTrue(manager.configurePin(pin("123456"), dek))

        val reloaded = AuthenticationManager(VaultStore(file), factor, FakeLockoutClock())

        assertArrayEquals(dek, (reloaded.unlockWithPin(pin("123456")) as PinUnlock.Success).dek)
    }

    @Test
    fun theCallersPinIsLeftForTheCallerToWipe() {
        val typed = pin("123456")
        assertTrue(manager.configurePin(typed, ByteArray(32) { 7 }))
        assertArrayEquals(pin("123456"), typed)
    }

    @Test
    fun wipeDeletesTheSlotAndTheDeviceKey() {
        assertTrue(manager.configurePin(pin("123456"), ByteArray(32) { 7 }))

        manager.wipe()

        assertFalse(manager.hasPinSlot())
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin(pin("123456")))
    }

    private fun pin(value: String) = value.toCharArray()
}
