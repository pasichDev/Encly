package com.pasich.encly.ios.security

import com.pasich.encly.core.keystore.BiometricKeychain
import com.pasich.encly.core.security.BiometricHost
import com.pasich.encly.core.security.VaultFileIo
import com.pasich.encly.core.security.VaultStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The states of the biometric slot that need no Face ID: with an in-memory vault store and a Keychain item of the
 * test's own, a slot entry with no wrap key, a wrap key with no slot entry and a refused enrolment all end with no
 * slot. The enrolment and the unlock themselves need a biometric match, which the Simulator cannot give.
 */
class IosBiometricSlotTest {
    private class MemoryVaultFileIo : VaultFileIo {
        private var bytes: ByteArray? = null

        override fun exists() = bytes != null

        override fun read(): ByteArray = checkNotNull(bytes)

        override fun deleteTemp() = Unit

        override fun replace(bytes: ByteArray, beforeRename: (() -> Unit)?) {
            this.bytes = bytes.copyOf()
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }

    private object ForeignHost : BiometricHost

    private val store = VaultStore(MemoryVaultFileIo())
    private val keychain = BiometricKeychain(service = "com.pasich.encly.test-slot", account = "slot-test")
    private val manager = IosBiometricManager(store, keychain, IosBiometricTexts())

    @AfterTest
    fun cleanUp() {
        keychain.delete()
    }

    @Test
    fun anEmptyStoreHasNoSlot() {
        assertFalse(manager.hasSlot())
        assertFalse(manager.takeInvalidated())
    }

    @Test
    fun aSlotEntryWithoutItsWrapKeyIsNoSlot() {
        store.edit { putBytes("bio.slot", ByteArray(40) { 1 }) }

        assertTrue(store.contains("bio.slot"))
        assertFalse(manager.hasSlot())
    }

    @Test
    fun unlockingWithNoSlotAnswersNullAtOnce() {
        var answered = false
        var key: ByteArray? = byteArrayOf(1)

        manager.unlock(IosBiometricHost) {
            answered = true
            key = it
        }

        assertTrue(answered)
        assertNull(key)
    }

    @Test
    fun aForeignHostCannotEnrolOrUnlock() {
        store.edit { putBytes("bio.slot", ByteArray(40) { 1 }) }
        var enrolled: Boolean? = null
        var unlocked: ByteArray? = byteArrayOf(1)

        manager.enroll(ForeignHost, ByteArray(32)) { enrolled = it }
        manager.unlock(ForeignHost) { unlocked = it }

        assertEquals(false, enrolled)
        assertNull(unlocked)
    }

    @Test
    fun aDataKeyOfTheWrongSizeIsNotEnrolled() {
        var enrolled: Boolean? = null

        manager.enroll(IosBiometricHost, ByteArray(16)) { enrolled = it }

        assertEquals(false, enrolled)
        assertFalse(manager.hasSlot())
    }
}
