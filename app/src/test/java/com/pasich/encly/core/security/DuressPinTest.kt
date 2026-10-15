package com.pasich.encly.core.security

import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.database.VaultFile
import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.eqValue
import com.pasich.encly.testutil.tempVaultStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * The duress PIN (issue #61): a third PIN that, typed on the lock screen, opens a decoy vault of
 * its own (separate DEK, separate database) and never touches the real one.
 */
class DuressPinTest {
    private lateinit var store: VaultStore
    private lateinit var factor: FakePinFactor
    private lateinit var auth: AuthenticationManager
    private lateinit var clock: FakeLockoutClock
    private val dek = ByteArray(DEK_LENGTH) { 0x3C }

    @Before
    fun setUp() {
        store = tempVaultStore()
        factor = FakePinFactor()
        clock = FakeLockoutClock()
        auth = AuthenticationManager(store, factor, clock)
        assertTrue(auth.configurePin(pin(PIN), dek))
    }

    // --- nothing shows whether a duress PIN is set ----------------------------------------

    @Test
    fun theStoreHasTheSameKeysAndSizesWithAndWithoutADuressPin() {
        val otherStore = tempVaultStore()
        val other = AuthenticationManager(otherStore, FakePinFactor(), FakeLockoutClock())
        assertTrue(other.configurePin(pin(PIN), dek))
        assertEquals(DuressPinChange.SET_NEW_VAULT, other.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertEquals(shape(store), shape(otherStore))
        assertTrue(store.contains(DECOY_SLOT) && store.contains(DECOY_KEY))
    }

    @Test
    fun anExistingVaultGetsRandomDuressEntriesWithoutTouchingThePinSlot() {
        store.edit {
            remove(DECOY_SLOT)
            remove(DECOY_KEY)
        }
        val before = store.getBytes(PIN_SLOT)!!

        assertTrue(auth.ensureWipeSlot())
        val slot = store.getBytes(DECOY_SLOT)!!
        assertTrue(auth.ensureWipeSlot())

        assertArrayEquals(before, store.getBytes(PIN_SLOT))
        assertArrayEquals("a second run writes nothing", slot, store.getBytes(DECOY_SLOT))
        assertFalse(auth.hasDecoyVault(dek))
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))
    }

    @Test
    fun onlyTheRealVaultsSessionCanTellADecoyVaultExists() {
        assertFalse(auth.hasDecoyVault(dek))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertTrue(auth.hasDecoyVault(dek))
        val decoyDek = (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek
        assertFalse("the decoy DEK does not reveal it", auth.hasDecoyVault(decoyDek))
    }

    // --- dispatch -----------------------------------------------------------------------

    @Test
    fun eachPinOpensItsOwnVault() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
        val decoyDek = (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek
        assertFalse(decoyDek.contentEquals(dek))
        assertArrayEquals(
            "the same decoy vault every time",
            decoyDek,
            (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek,
        )
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin("000000")))
        // The duress PIN changed nothing: the real vault and the wipe PIN are as they were.
        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
    }

    @Test
    fun everyAttemptRunsTheHardwareHalfOnce() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        listOf(PIN, DURESS_PIN, "000000").forEach {
            val before = factor.calls
            auth.unlockWithPin(pin(it))
            assertEquals("one KDF run for $it", before + 1, factor.calls)
        }
    }

    @Test
    fun theDuressPinIsCountedAndThenClearsTheLockoutLikeTheRightPin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        repeat(AuthenticationManager.MAX_ATTEMPTS - 1) { auth.unlockWithPin(pin("000000")) }
        assertTrue(store.getInt(FAILURES, 0) > 0)

        assertTrue(auth.unlockWithPin(pin(DURESS_PIN)) is PinUnlock.Decoy)

        assertEquals(0, store.getInt(FAILURES, 0))
        assertEquals(
            "the duress PIN itself is no strike",
            AuthenticationManager.MAX_ATTEMPTS - 1,
            store.getInt(STRIKES, 0),
        )
    }

    @Test
    fun theDuressPinBetweenGuessesNeverBuysUnlimitedGuessesAtThePin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        val free = AuthenticationManager.MAX_ATTEMPTS - 1
        var guesses = 0
        var locked = false
        // Four wrong guesses, the duress PIN, again: the coercer's loop.
        while (!locked && guesses < 100) {
            repeat(free) {
                if (auth.unlockWithPin(pin("000000")) == PinUnlock.LockedOut) locked = true else guesses++
            }
            if (!locked) assertTrue(auth.unlockWithPin(pin(DURESS_PIN)) is PinUnlock.Decoy)
        }

        assertEquals(
            "free guesses end after the grace",
            AuthenticationManager.MAX_ATTEMPTS + AuthenticationManager.STRIKE_GRACE,
            guesses,
        )
        // Past the grace every miss is locked out, even right after the duress PIN, and longer each time.
        var lastPenalty = 0L
        repeat(3) {
            clock.elapsed += auth.remainingLockoutMillis()
            assertTrue(auth.unlockWithPin(pin(DURESS_PIN)) is PinUnlock.Decoy)
            assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin("000000")))
            val penalty = auth.remainingLockoutMillis()
            assertTrue("locked out at once: $penalty", penalty > lastPenalty)
            lastPenalty = penalty
        }
    }

    @Test
    fun theRightPinStartsTheStrikesOver() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        repeat(2) {
            repeat(AuthenticationManager.MAX_ATTEMPTS - 1) { auth.unlockWithPin(pin("000000")) }
            assertTrue(auth.unlockWithPin(pin(DURESS_PIN)) is PinUnlock.Decoy)
        }
        assertTrue(store.getInt(STRIKES, 0) > AuthenticationManager.MAX_ATTEMPTS)

        assertTrue(auth.unlockWithPin(pin(PIN)) is PinUnlock.Success)

        assertFalse(store.contains(STRIKES))
        repeat(AuthenticationManager.MAX_ATTEMPTS - 1) { auth.unlockWithPin(pin("000000")) }
        assertEquals(0L, auth.remainingLockoutMillis())
    }

    @Test
    fun aHardwareFailureGivesTheStrikeBackAndAKillMidCheckKeepsIt() {
        repeat(2) { auth.unlockWithPin(pin("000000")) }
        factor.failing = true

        assertEquals(PinUnlock.Failed, auth.unlockWithPin(pin("000000")))

        assertEquals(2, store.getInt(STRIKES, 0))
        factor.failing = false
        // Killed during the KDF: the strike was written before it ran and is never refunded.
        val killing = AuthenticationManager(
            store,
            object : PinHardwareFactor by factor {
                override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray = error("killed")
            },
            clock,
        )
        runCatching { killing.unlockWithPin(pin("000000")) }
        assertEquals(3, store.getInt(STRIKES, 0))
    }

    @Test
    fun refusingAnotherPinInSettingsCostsAMissAndALockoutRefusesIt() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertEquals(WipePinChange.IN_USE, auth.configureWipePin(pin(DURESS_PIN)))
        assertEquals(WipePinChange.SAME_AS_PIN, auth.configureWipePin(pin(PIN)))
        assertEquals(PinChange.IN_USE, auth.changePin(pin(DURESS_PIN), dek))
        assertEquals(DuressPinChange.SAME_AS_PIN, auth.configureDuressPin(pin(PIN), dek) { true })
        assertEquals(4, store.getInt(FAILURES, 0))

        assertEquals(WipePinChange.IN_USE, auth.configureWipePin(pin(DURESS_PIN)))
        assertTrue(auth.remainingLockoutMillis() > 0)
        assertEquals(WipePinChange.FAILED, auth.configureWipePin(pin(DURESS_PIN)))
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(pin(OTHER_DURESS_PIN), dek) { true })
        assertEquals(PinUnlock.LockedOut, auth.unlockWithPin(pin(PIN)))
    }

    @Test
    fun aRunningLockoutRefusesTheDuressPin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        repeat(AuthenticationManager.MAX_ATTEMPTS) { auth.unlockWithPin(pin("000000")) }

        assertEquals(PinUnlock.LockedOut, auth.unlockWithPin(pin(DURESS_PIN)))
    }

    @Test
    fun aDecoySessionVerifiesOnlyTheDuressPinAndTheRealSessionOnlyThePin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertTrue(auth.verifyPinAuth(pin(PIN)))
        assertFalse(auth.verifyPinAuth(pin(DURESS_PIN)))
        assertTrue(auth.verifyPinAuth(pin(DURESS_PIN), decoy = true))
        assertFalse("no oracle for the real PIN", auth.verifyPinAuth(pin(PIN), decoy = true))
    }

    // --- collisions ---------------------------------------------------------------------

    @Test
    fun theDuressPinMustDifferFromThePinAndTheWipePin() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))

        assertEquals(DuressPinChange.SAME_AS_PIN, auth.configureDuressPin(pin(PIN), dek) { true })
        assertEquals(DuressPinChange.IN_USE, auth.configureDuressPin(pin(WIPE_PIN), dek) { true })
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(pin("12345"), dek) { true })
        assertFalse(auth.hasDecoyVault(dek))
        assertTrue("the wipe PIN still wipes", auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
    }

    @Test
    fun theWipePinMayNotBeTheDuressPin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertEquals(WipePinChange.IN_USE, auth.configureWipePin(pin(DURESS_PIN)))
        assertTrue(auth.unlockWithPin(pin(DURESS_PIN)) is PinUnlock.Decoy)
    }

    @Test
    fun changingThePinToTheDuressPinIsRefusedAndKeepsBoth() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        assertEquals(PinChange.IN_USE, auth.changePin(pin(DURESS_PIN), dek))

        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
        assertTrue(auth.unlockWithPin(pin(DURESS_PIN)) is PinUnlock.Decoy)
    }

    @Test
    fun changingThePinKeepsTheDuressPin() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        val decoyDek = (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek

        assertEquals(PinChange.SET, auth.changePin(pin(NEW_PIN), dek))

        assertArrayEquals(decoyDek, (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek)
        assertArrayEquals(dek, (auth.unlockWithPin(pin(NEW_PIN)) as PinUnlock.Success).dek)
        assertFalse(auth.duressPinTurnedOff(dek))
    }

    @Test
    fun aPinKeyResetTurnsTheDuressPinOffSaysSoAndSettingItAgainKeepsTheNotes() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        val decoyDek = (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek
        factor.lost = true

        assertTrue(auth.configurePin(pin(NEW_PIN), dek))

        assertTrue(auth.duressPinTurnedOff(dek))
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))
        var newVault = false
        assertEquals(
            DuressPinChange.SET,
            auth.configureDuressPin(pin(DURESS_PIN), dek) {
                newVault = true
                true
            },
        )
        assertFalse("the decoy database is kept", newVault)
        assertFalse(auth.duressPinTurnedOff(dek))
        assertArrayEquals(decoyDek, (auth.unlockWithPin(pin(DURESS_PIN)) as PinUnlock.Decoy).dek)
    }

    @Test
    fun aResetWithoutADecoyVaultSaysNothing() {
        factor.lost = true

        assertTrue(auth.configurePin(pin(NEW_PIN), dek))

        assertFalse(auth.duressPinTurnedOff(dek))
    }

    @Test
    fun aPinSetWithANewSaltTurnsTheDuressPinOffAndSettingsSaysSo() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        // The PIN slot is unreadable (recovery phrase unlock, then a new PIN): a new salt.
        store.edit { remove(PIN_SLOT) }

        assertTrue(auth.configurePin(pin(NEW_PIN), dek))

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))
        assertTrue(auth.hasDecoyVault(dek))
        assertTrue(auth.duressPinTurnedOff(dek))
        assertEquals(DuressPinChange.SET, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        assertFalse(auth.duressPinTurnedOff(dek))
    }

    @Test
    fun everyLifecycleStepLeavesTheSameStoreShapeWithAndWithoutADecoyVault() {
        val plain = tempVaultStore()
        val plainFactor = FakePinFactor()
        val other = AuthenticationManager(plain, plainFactor, FakeLockoutClock())
        assertTrue(other.configurePin(pin(PIN), dek))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        fun same(step: String) = assertEquals(step, shape(plain), shape(store))
        same("set")

        assertEquals(PinChange.SET, other.changePin(pin(NEW_PIN), dek))
        assertEquals(PinChange.SET, auth.changePin(pin(NEW_PIN), dek))
        same("PIN change")

        plainFactor.lost = true
        factor.lost = true
        assertTrue(other.configurePin(pin(PIN), dek))
        assertTrue(auth.configurePin(pin(PIN), dek))
        same("key reset")

        plain.edit { remove(PIN_SLOT) }
        store.edit { remove(PIN_SLOT) }
        assertTrue(other.configurePin(pin(NEW_PIN), dek))
        assertTrue(auth.configurePin(pin(NEW_PIN), dek))
        same("new salt")

        assertTrue(other.removeDuressPin())
        assertTrue(auth.removeDuressPin())
        same("removed")

        assertEquals(WipePinChange.SET, other.configureWipePin(pin(WIPE_PIN)))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        assertTrue(other.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
        same("erase")
    }

    @Test
    fun aNewDecoyVaultFirstClearsWhatAnEarlierOneLeftAndStopsIfThatFails() {
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(pin(DURESS_PIN), dek) { false })
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))

        var cleared = 0
        assertEquals(
            DuressPinChange.SET_NEW_VAULT,
            auth.configureDuressPin(pin(DURESS_PIN), dek) {
                cleared++
                true
            },
        )
        assertEquals(
            DuressPinChange.SET,
            auth.configureDuressPin(pin(OTHER_DURESS_PIN), dek) {
                cleared++
                true
            },
        )
        assertEquals(1, cleared)
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))
        assertTrue(auth.unlockWithPin(pin(OTHER_DURESS_PIN)) is PinUnlock.Decoy)
    }

    @Test
    fun removingTheDuressPinMakesItAWrongPinAndKeepsTheShape() {
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })
        val before = shape(store)

        assertTrue(auth.removeDuressPin())

        assertEquals(before, shape(store))
        assertFalse(auth.hasDecoyVault(dek))
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))
        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
    }

    @Test
    fun theWipePinAlsoErasesTheDecoyVault() {
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(pin(DURESS_PIN), dek) { true })

        val erased = auth.unlockWithPin(pin(WIPE_PIN)) as PinUnlock.Erased

        assertFalse(auth.hasDecoyVault(erased.dek))
        assertFalse(auth.hasDecoyVault(dek))
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(DURESS_PIN)))
        assertTrue(store.contains(DECOY_SLOT) && store.contains(DECOY_KEY))
    }

    // --- the session --------------------------------------------------------------------

    @Test
    fun theDuressPinOpensTheDecoyDatabaseAndNeverTheRealOne() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(true)

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(DURESS_PIN)))

        verify(vault.database).unlockDatabase(anyByteArray(), eq(true), eqValue(VaultFile.DECOY))
        verify(vault.database, never()).unlockDatabase(anyByteArray(), anyBoolean())
        verify(vault.database, never()).wipe()
        assertTrue(vault.security.isDecoySession())
    }

    @Test
    fun anExistingDecoyDatabaseIsOpenedNotCreated() {
        val vault = Vault()
        `when`(vault.database.hasEncryptedDatabase(VaultFile.DECOY)).thenReturn(true)
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(true)

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(DURESS_PIN)))

        verify(vault.database).unlockDatabase(anyByteArray(), eq(false), eqValue(VaultFile.DECOY))
    }

    @Test
    fun aDecoyDatabaseThatDoesNotOpenFailsWithoutFallingBack() {
        val vault = Vault()
        `when`(vault.database.hasEncryptedDatabase(VaultFile.DECOY)).thenReturn(true)
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(false)

        assertEquals(VaultUnlockResult.DB_ERROR, vault.security.unlockWithPin(pin(DURESS_PIN)))

        verify(vault.database, never()).unlockDatabase(anyByteArray(), anyBoolean())
        verify(vault.database, never()).wipeIfClosed(VaultFile.DECOY)
        assertFalse(vault.security.isDecoySession())
        assertFalse(vault.security.isDatabaseUnlocked())
    }

    @Test
    fun aDecoySessionCanNotTouchTheRealVaultsKeys() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(true)
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(DURESS_PIN)))
        val before = vault.store.keys().associateWith { vault.store.getBytes(it)!!.toList() }

        assertEquals(PinChange.FAILED, vault.security.changePin(pin(NEW_PIN)))
        assertFalse(vault.security.configurePin(pin(NEW_PIN)))
        assertEquals(WipePinChange.FAILED, vault.security.configureWipePin(pin(WIPE_PIN)))
        assertFalse(vault.security.removeWipePin())
        assertEquals(DuressPinChange.FAILED, vault.security.configureDuressPin(pin(OTHER_DURESS_PIN)))
        assertFalse(vault.security.removeDuressPin())
        assertFalse(vault.security.hasDuressPin())
        assertNull(vault.security.copyBackupRootKey())
        assertFalse(vault.security.addRecoverySeed(vault.seed.generateMnemonic().chars))
        assertFalse(vault.security.replaceRecoverySeed(vault.seed.generateMnemonic().chars))
        assertFalse(vault.security.createBackupKey(vault.seed.generateMnemonic().chars))
        assertFalse(vault.security.canResetPinWithoutCurrent())
        vault.security.disableBiometric()
        assertFalse(vault.security.wipeAndReset())

        verify(vault.biometric, never()).disable()
        verify(vault.database, never()).wipe()
        assertEquals(before, vault.store.keys().associateWith { vault.store.getBytes(it)!!.toList() })
        assertTrue(vault.security.isDecoySession())
    }

    @Test
    fun aDecoySessionReChecksWithTheDuressPin() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(true)
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(DURESS_PIN)))

        assertTrue(vault.security.verifyPin(pin(DURESS_PIN)))
        assertFalse(vault.security.verifyPin(pin(PIN)))
    }

    @Test
    fun switchingVaultsChangesTheEpochButReopeningTheSameOneDoesNot() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(true)

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))
        val real = vault.security.eraseEpoch()
        vault.security.lock()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))
        assertEquals(real, vault.security.eraseEpoch())
        vault.security.lock()

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(DURESS_PIN)))
        val decoy = vault.security.eraseEpoch()
        assertNotEquals(real, decoy)
        assertFalse(vault.security.getSettingsAuth().hasDuressPin)
        vault.security.lock()

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))
        assertNotEquals(decoy, vault.security.eraseEpoch())
        assertFalse(vault.security.isDecoySession())
        assertTrue(vault.security.getSettingsAuth().hasDuressPin)
    }

    @Test
    fun removingTheDuressPinDeletesOnlyTheDecoyDatabase() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        `when`(vault.database.wipeIfClosed(VaultFile.DECOY)).thenReturn(true)
        assertFalse("locked", vault.security.removeDuressPin())
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))

        assertTrue(vault.security.removeDuressPin())

        verify(vault.database).wipeIfClosed(VaultFile.DECOY)
        verify(vault.database, never()).wipe()
        assertFalse(vault.security.hasDuressPin())
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, vault.security.unlockWithPin(pin(DURESS_PIN)))
    }

    @Test
    fun settingTheDuressPinWipesTheTypedPin() {
        val vault = Vault(withDuressPin = false)
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        `when`(vault.database.wipeIfClosed(VaultFile.DECOY)).thenReturn(true)
        assertEquals(DuressPinChange.FAILED, vault.security.configureDuressPin(pin(DURESS_PIN)))
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))

        val typed = pin(DURESS_PIN)
        assertEquals(DuressPinChange.SET_NEW_VAULT, vault.security.configureDuressPin(typed))

        assertTrue(typed.all { it == '\u0000' })
        verify(vault.database).wipeIfClosed(VaultFile.DECOY)
        assertTrue(vault.security.hasDuressPin())
    }

    @Test
    fun aDecoyCanNotOpenWhileAnEraseIsPending() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))).thenReturn(true)
        vault.store.edit { putInt("wipe.pending", WipeStage.DATABASE.ordinal) }

        assertEquals(VaultUnlockResult.DB_ERROR, vault.security.unlockWithPin(pin(DURESS_PIN)))

        verify(vault.database, never()).unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultFile.DECOY))
    }

    /** A committed vault with a recovery phrase, the PIN and (by default) a duress PIN. */
    private inner class Vault(withDuressPin: Boolean = true) {
        val store = this@DuressPinTest.store
        val auth = this@DuressPinTest.auth
        val prefs = InMemorySharedPreferences()
        val seed = SeedPhraseManager(store)
        val database: SecureDatabaseManager = mock(SecureDatabaseManager::class.java)
        val biometric: BiometricManager = mock(BiometricManager::class.java)
        val security = SecurityManager(prefs, store, seed, database, auth, biometric) {}

        init {
            assertTrue(seed.initializeVault(seed.generateMnemonic().chars))
            val bootstrap = seed.copyBootstrapKey()!!
            assertTrue(auth.configurePin(pin(PIN), bootstrap))
            if (withDuressPin) {
                assertEquals(
                    DuressPinChange.SET_NEW_VAULT,
                    auth.configureDuressPin(pin(DURESS_PIN), bootstrap) {
                        true
                    },
                )
            }
            prefs.edit()
                .putBoolean("onboarding_shown_v3", true)
                .putLong(BackupManager.LAST_EXPORT_KEY, 1L)
                .commit()
        }
    }

    /** Every key and the length of its value. */
    private fun shape(store: VaultStore): Map<String, Int> =
        store.keys().toSortedSet().associateWith { store.getBytes(it)!!.size }

    private fun pin(value: String) = value.toCharArray()

    private companion object {
        const val PIN = "135790"
        const val NEW_PIN = "246813"
        const val WIPE_PIN = "864200"
        const val DURESS_PIN = "975310"
        const val OTHER_DURESS_PIN = "112358"
        const val DEK_LENGTH = 32
        const val PIN_SLOT = "pin.slot"
        const val DECOY_SLOT = "pin.decoy.slot"
        const val DECOY_KEY = "decoy.key"
        const val FAILURES = "lockout.failures"
        const val STRIKES = "pin.strikes"
    }
}
