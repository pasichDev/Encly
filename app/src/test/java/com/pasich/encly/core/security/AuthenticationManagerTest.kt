package com.pasich.encly.core.security

import com.pasich.encly.testutil.InMemorySharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthenticationManagerTest {

    private lateinit var preferences: InMemorySharedPreferences
    private lateinit var manager: AuthenticationManager

    @Before
    fun setUp() {
        preferences = InMemorySharedPreferences()
        manager = AuthenticationManager(preferences)
    }

    @Test
    fun correctPinUnwrapsTheConfiguredDek() {
        val dek = ByteArray(32) { 0x5A }
        assertTrue(manager.configurePin("123456", dek))

        val unwrapped = manager.unlockWithPin("123456")

        assertArrayEquals(dek, unwrapped)
        assertTrue(manager.hasPinSlot())
        assertTrue(manager.verifyPinAuth("123456"))
    }

    @Test
    fun wrongPinNeverUnwrapsTheDek() {
        val dek = ByteArray(32) { 0x2A }
        assertTrue(manager.configurePin("123456", dek))

        assertNull(manager.unlockWithPin("654321"))
        assertTrue(manager.remainingLockoutMillis() == 0L)
        assertArrayEquals(dek, manager.unlockWithPin("123456"))
    }

    @Test
    fun tamperedPinSlotFailsClosed() {
        val dek = ByteArray(32) { 0x33 }
        assertTrue(manager.configurePin("123456", dek))
        preferences.edit()
            .putString("v2_pin_slot", "not-a-valid-slot")
            .commit()

        assertNull(manager.unlockWithPin("123456"))
    }

    @Test
    fun fiveFailedAttemptsStartALockoutAndRejectTheCorrectPin() {
        val dek = ByteArray(32) { 0x11 }
        assertTrue(manager.configurePin("123456", dek))

        repeat(5) {
            assertNull(manager.unlockWithPin("000000"))
        }

        assertTrue(manager.remainingLockoutMillis() > 0L)
        assertNull(manager.unlockWithPin("123456"))
    }

    @Test
    fun incompletePinMetadataIsNotTreatedAsASlot() {
        preferences.edit()
            .putString("v2_pin_salt", "present-without-slot")
            .commit()

        assertFalse(manager.hasPinSlot())
        assertNull(manager.unlockWithPin("123456"))
    }
}
