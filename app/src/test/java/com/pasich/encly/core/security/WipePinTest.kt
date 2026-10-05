package com.pasich.encly.core.security

import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.tempVaultFile
import com.pasich.encly.testutil.tempVaultStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The wipe PIN (issue #52): a second PIN that, typed on the lock screen, erases the vault and
 * opens an empty one, while nothing in the store shows whether one is set.
 */
class WipePinTest {
    private lateinit var file: File
    private lateinit var store: VaultStore
    private lateinit var factor: FakePinFactor
    private lateinit var clock: FakeLockoutClock
    private lateinit var auth: AuthenticationManager
    private val dek = ByteArray(DEK_LENGTH) { 0x3C }

    @Before
    fun setUp() {
        file = tempVaultFile()
        store = VaultStore(file)
        factor = FakePinFactor()
        clock = FakeLockoutClock()
        auth = AuthenticationManager(store, factor, clock)
    }

    // --- nothing shows whether a wipe PIN is set -----------------------------------------

    @Test
    fun theStoreHasTheSameKeysAndSizesWithAndWithoutAWipePin() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        val otherStore = tempVaultStore()
        val other = AuthenticationManager(otherStore, FakePinFactor(), FakeLockoutClock())
        assertTrue(other.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, other.configureWipePin(pin(WIPE_PIN)))

        assertEquals(shape(store), shape(otherStore))
    }

    @Test
    fun aDecoyAndARealWipeSlotHaveTheSameLengthAndVersion() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        val decoy = store.getBytes(WIPE_SLOT)!!
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        val real = store.getBytes(WIPE_SLOT)!!

        assertEquals(decoy.size, real.size)
        assertEquals(decoy[0], real[0])
        // No salt of its own: the PIN slot's salt appears nowhere in it.
        val salt = store.getBytes(PIN_SLOT)!!.copyOfRange(1, 1 + SALT_SIZE)
        assertFalse(real.toList().windowed(SALT_SIZE).any { it == salt.toList() })
    }

    @Test
    fun anExistingVaultKeepsItsPinSlotBytesWhenTheDecoyIsAdded() {
        // A store as builds before the wipe PIN left it: a PIN slot, no wipe slot, no key slot.
        assertTrue(auth.configurePin(pin(PIN), dek))
        store.edit {
            remove(WIPE_SLOT)
            remove(KEY_SLOT)
        }
        val before = store.getBytes(PIN_SLOT)!!

        assertTrue(auth.ensureWipeSlot())
        val decoy = store.getBytes(WIPE_SLOT)!!
        assertTrue(auth.ensureWipeSlot())

        assertArrayEquals(before, store.getBytes(PIN_SLOT))
        assertArrayEquals("a second run writes nothing", decoy, store.getBytes(WIPE_SLOT))
        assertEquals(PinKeySlot.A.ordinal, store.getInt(KEY_SLOT, -1))
        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
    }

    @Test
    fun aVaultWithoutAPinSlotGetsNoDecoy() {
        assertTrue(auth.ensureWipeSlot())

        assertFalse(store.contains(WIPE_SLOT))
    }

    @Test
    fun anOlderBuildThatIgnoresTheNewKeysStillOpensTheVault() {
        // Downgrade: an older build reads the same file and knows only pin.slot under key A.
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        val olderStore = VaultStore(file)
        assertFalse(olderStore.isCorrupt())
        olderStore.edit { removePrefix("pin.wipe.") }
        olderStore.edit { remove(KEY_SLOT) }

        val older = AuthenticationManager(olderStore, factor, FakeLockoutClock())

        assertArrayEquals(dek, (older.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
    }

    // --- the erase -----------------------------------------------------------------------

    @Test
    fun theWipePinErasesEverySlotAndTheOldPinIsWrongAfterwards() {
        val seed = SeedPhraseManager(store)
        assertTrue(seed.initializeVault(seed.generateMnemonic().chars))
        val oldDek = seed.copyBootstrapKey()!!
        assertTrue(auth.configurePin(pin(PIN), oldDek))
        store.edit {
            putBytes("bio.slot", ByteArray(60) { 1 })
            putBoolean("auth.biometric_enabled", true)
        }
        auth.unlockWithPin(pin("000000"))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))

        val erased = auth.unlockWithPin(pin(WIPE_PIN))

        assertTrue(erased is PinUnlock.Erased)
        val newDek = (erased as PinUnlock.Erased).dek
        assertFalse(newDek.contentEquals(oldDek))
        assertEquals(WipeStage.DATABASE, auth.pendingWipe())
        val keys = keysOf(store)
        assertTrue(keys.none { it.startsWith("recovery.") || it.startsWith("backup.") || it.startsWith("bio.") })
        assertTrue(keys.none { it.startsWith("lockout.") || it == "auth.biometric_enabled" })
        // vault.version stays: the empty vault is a committed vault, not onboarding.
        assertTrue(seed.verificationKeyData())
        assertFalse(seed.hasRecoverySeed())
        assertFalse(seed.hasBackupKey())
        // The PIN slot moved to the other Keystore key; the old one goes with the cleanup.
        assertEquals(PinKeySlot.B.ordinal, store.getInt(KEY_SLOT, -1))
        assertTrue(factor.hasKey(PinKeySlot.B))
        auth.deleteRetiredPinKey()
        assertFalse(factor.hasKey(PinKeySlot.A))

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(PIN)))
        // The wipe PIN is now the PIN of the empty vault, and wipes nothing more.
        assertArrayEquals(newDek, (auth.unlockWithPin(pin(WIPE_PIN)) as PinUnlock.Success).dek)
    }

    @Test
    fun aSecondEraseMovesTheKeyBackToTheFirstSlot() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
        auth.deleteRetiredPinKey()
        assertTrue(auth.clearPendingWipe())
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(PIN)))

        assertTrue(auth.unlockWithPin(pin(PIN)) is PinUnlock.Erased)

        assertEquals(PinKeySlot.A.ordinal, store.getInt(KEY_SLOT, -1))
        assertTrue(auth.unlockWithPin(pin(PIN)) is PinUnlock.Success)
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(WIPE_PIN)))
    }

    @Test
    fun withoutASecondKeySlotTheEraseStillReplacesTheSlots() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        factor.failingReset = true

        val erased = auth.unlockWithPin(pin(WIPE_PIN))

        assertTrue(erased is PinUnlock.Erased)
        assertEquals(PinKeySlot.A.ordinal, store.getInt(KEY_SLOT, -1))
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(PIN)))
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Success)
    }

    @Test
    fun insideTheOpenVaultTheWipePinIsJustAWrongPin() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))

        assertFalse(auth.verifyPinAuth(pin(WIPE_PIN)))

        assertNull(auth.pendingWipe())
        assertTrue(auth.verifyPinAuth(pin(PIN)))
    }

    @Test
    fun withoutAWipePinNothingButThePinOpens() {
        assertTrue(auth.configurePin(pin(PIN), dek))

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(WIPE_PIN)))
        assertNull(auth.pendingWipe())
    }

    @Test
    fun everyAttemptRunsTheHardwareHalfOnce() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        val before = factor.calls

        auth.unlockWithPin(pin(PIN))
        auth.unlockWithPin(pin("000000"))

        assertEquals(before + 2, factor.calls)
    }

    @Test
    fun theWipePinTakesAboutAsLongAsThePin() {
        // One KDF run either way; the erase only adds a key, a seal and a store write.
        val normal = (1..2).minOf {
            val manager = configured(withWipePin = true)
            timed { manager.unlockWithPin(pin(PIN)) }
        }
        val wipe = (1..2).minOf {
            val manager = configured(withWipePin = true)
            timed { assertTrue(manager.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased) }
        }

        assertTrue("normal $normal ms, wipe $wipe ms", wipe <= normal * 2 + TIMING_SLACK_MS)
    }

    // --- setting, changing and removing ---------------------------------------------------

    @Test
    fun theWipePinMustBeSixDigitsAndDifferFromThePin() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        val decoy = store.getBytes(WIPE_SLOT)!!

        assertEquals(WipePinChange.SAME_AS_PIN, auth.configureWipePin(pin(PIN)))
        assertEquals(WipePinChange.FAILED, auth.configureWipePin(pin("12345")))
        assertEquals(WipePinChange.FAILED, auth.configureWipePin(pin("12345a")))

        assertArrayEquals(decoy, store.getBytes(WIPE_SLOT))
    }

    @Test
    fun aWipePinNeedsAPinSlot() {
        assertEquals(WipePinChange.FAILED, auth.configureWipePin(pin(WIPE_PIN)))
    }

    @Test
    fun changingThePinKeepsTheWipePin() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))

        assertTrue(auth.configurePin(pin("246810"), dek))

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(PIN)))
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
    }

    @Test
    fun makingTheWipePinThePinTurnsTheWipePinOff() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))

        assertTrue(auth.configurePin(pin(WIPE_PIN), dek))

        assertArrayEquals(dek, (auth.unlockWithPin(pin(WIPE_PIN)) as PinUnlock.Success).dek)
        assertNull(auth.pendingWipe())
    }

    @Test
    fun removingTheWipePinMakesItAWrongPin() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        val real = store.getBytes(WIPE_SLOT)!!

        assertTrue(auth.removeWipePin())

        assertEquals(real.size, store.getBytes(WIPE_SLOT)!!.size)
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(WIPE_PIN)))
        assertNull(auth.pendingWipe())
    }

    @Test
    fun aPinKeyResetTurnsTheWipePinOffAndSaysSo() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        assertFalse(auth.wipePinTurnedOff())
        // The Keystore key was invalidated; a new PIN is set after a recovery-phrase unlock.
        factor.lost = true

        assertTrue(auth.configurePin(pin("246810"), dek))

        assertTrue(auth.wipePinTurnedOff())
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(WIPE_PIN)))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        assertFalse(auth.wipePinTurnedOff())
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
    }

    @Test
    fun aPinKeyTheSystemDeletedTurnsTheWipePinOffAndSaysSo() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        // Not invalidated but gone: setting a PIN has to make a new key, which the wipe slot was
        // not sealed with.
        factor.delete(PinKeySlot.A)

        assertTrue(auth.configurePin(pin("246810"), dek))

        assertTrue(auth.wipePinTurnedOff())
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin(WIPE_PIN)))
        assertArrayEquals(dek, (auth.unlockWithPin(pin("246810")) as PinUnlock.Success).dek)
    }

    @Test
    fun anEraseThatFailsLeavesNoSecondKeyAndCanBeTriedAgain() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        // The new key was made, but deriving the new slot's key with it fails.
        factor.failingSlots = setOf(PinKeySlot.B)

        assertEquals(PinUnlock.Failed, auth.unlockWithPin(pin(WIPE_PIN)))

        assertFalse(factor.hasKey(PinKeySlot.B))
        assertNull(auth.pendingWipe())
        factor.failingSlots = emptySet()
        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
    }

    @Test
    fun anEraseWhoseStoreWriteFailsLeavesNoSecondKey() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        // The attempt is counted (one write), then phase 2's write fails.
        var writes = 0
        store.beforeRename = { if (++writes == 2) throw IOException("disk full") }

        assertEquals(PinUnlock.Failed, auth.unlockWithPin(pin(WIPE_PIN)))

        store.beforeRename = null
        assertFalse(factor.hasKey(PinKeySlot.B))
        assertNull(auth.pendingWipe())
        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
    }

    @Test
    fun removingTheWipePinAlsoClearsTheNotice() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        factor.lost = true
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertTrue(auth.wipePinTurnedOff())

        assertTrue(auth.removeWipePin())

        assertFalse(auth.wipePinTurnedOff())
    }

    // --- lockout ---------------------------------------------------------------------------

    @Test
    fun aRunningLockoutRefusesTheWipePinWithoutErasing() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        repeat(AuthenticationManager.MAX_ATTEMPTS) { auth.unlockWithPin(pin("000000")) }

        assertEquals(PinUnlock.LockedOut, auth.unlockWithPin(pin(WIPE_PIN)))

        assertNull(auth.pendingWipe())
        clock.elapsed += AuthenticationManager.FIRST_LOCKOUT_MS
        assertArrayEquals(dek, (auth.unlockWithPin(pin(PIN)) as PinUnlock.Success).dek)
    }

    @Test
    fun theWipePinIsCountedAndThenClearsTheLockoutLikeTheRightPin() {
        assertTrue(auth.configurePin(pin(PIN), dek))
        assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
        repeat(AuthenticationManager.MAX_ATTEMPTS - 1) { auth.unlockWithPin(pin("000000")) }

        assertTrue(auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)

        assertTrue(keysOf(store).none { it.startsWith("lockout.") })
        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(pin("000000")))
        assertEquals(0L, auth.remainingLockoutMillis())
    }

    // --- phase 3 and a kill between the phases ---------------------------------------------

    @Test
    fun aKillBeforeTheCleanupIsFinishedAtTheNextStart() {
        val vault = Vault()
        // Phases 1 and 2 ran; the process died before phase 3.
        assertTrue(vault.auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)

        val restarted = vault.restart()
        assertEquals(InitialStatus.AUTH, restarted.resolveInitialStatus())

        verify(vault.database).wipeIfClosed()
        verify(vault.biometric).disable()
        assertFalse(vault.prefs.contains(BackupManager.LAST_EXPORT_KEY))
        assertFalse(vault.factor.hasKey(PinKeySlot.A))
        // The empty database is created by the next unlock, with the new DEK.
        assertEquals(WipeStage.DATABASE, vault.auth.pendingWipe())
        `when`(vault.database.unlockDatabase(anyByteArray(), eq(true))).thenReturn(true)

        assertEquals(VaultUnlockResult.SUCCESS, restarted.unlockWithPin(pin(WIPE_PIN)))

        verify(vault.database).unlockDatabase(anyByteArray(), eq(true))
        assertEquals(WipeStage.CLEANUP, vault.auth.pendingWipe())
        restarted.completePendingWipe()
        assertNull(vault.auth.pendingWipe())
    }

    @Test
    fun aKillAfterTheEmptyDatabaseExistsOnlyCleansUp() {
        val vault = Vault()
        assertTrue(vault.auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
        assertTrue(vault.auth.markWipeDatabaseCreated())
        `when`(vault.database.hasEncryptedDatabase()).thenReturn(true)

        assertEquals(InitialStatus.AUTH, vault.restart().resolveInitialStatus())

        verify(vault.database, never()).wipe()
        verify(vault.database, never()).wipeIfClosed()
        verify(vault.biometric).disable()
        assertNull(vault.auth.pendingWipe())
    }

    @Test
    fun theWipePinOnTheLockScreenOpensAnEmptyVault() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        val epoch = vault.security.eraseEpoch()

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(WIPE_PIN)))

        // The old database goes before the new one is created (same file name).
        val order = inOrder(vault.database)
        order.verify(vault.database).wipe()
        order.verify(vault.database).unlockDatabase(anyByteArray(), eq(true))
        assertNotEquals(epoch, vault.security.eraseEpoch())
        // The rest waits until the vault is shown, so the wipe PIN does not wait for it.
        assertEquals(WipeStage.CLEANUP, vault.auth.pendingWipe())
        assertTrue(vault.factor.hasKey(PinKeySlot.A))
        vault.security.completePendingWipe()
        assertNull(vault.auth.pendingWipe())
        assertFalse(vault.factor.hasKey(PinKeySlot.A))
        assertFalse(vault.prefs.contains(BackupManager.LAST_EXPORT_KEY))
        // Onboarding stays committed: a restart asks for the PIN of the empty vault.
        `when`(vault.database.hasEncryptedDatabase()).thenReturn(true)
        assertEquals(InitialStatus.AUTH, vault.restart().resolveInitialStatus())

        // Locking does not bring the old vault's epoch back.
        val erased = vault.security.eraseEpoch()
        vault.security.lock()
        assertEquals(erased, vault.security.eraseEpoch())
    }

    @Test
    fun afterTheWipeAColdStartOpensTheEmptyVaultWithTheWipePinAndNotTheOldPin() {
        val vault = Vault()
        val keys = mutableListOf<Pair<ByteArray, Boolean>>()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenAnswer {
            keys += (it.arguments[0] as ByteArray).copyOf() to (it.arguments[1] as Boolean)
            true
        }
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(WIPE_PIN)))
        val (emptyVaultKey, created) = keys.single()
        assertTrue(created)
        vault.security.lock()

        // A new process: every file read back from disk.
        val store = VaultStore(file)
        val auth = AuthenticationManager(store, factor, clock)
        val database: SecureDatabaseManager = mock(SecureDatabaseManager::class.java)
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenAnswer {
            keys += (it.arguments[0] as ByteArray).copyOf() to (it.arguments[1] as Boolean)
            true
        }
        val security =
            SecurityManager(vault.prefs, store, SeedPhraseManager(store), database, auth, vault.biometric)
        assertEquals(InitialStatus.AUTH, security.resolveInitialStatus())

        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, security.unlockWithPin(pin(PIN)))
        assertEquals(VaultUnlockResult.SUCCESS, security.unlockWithPin(pin(WIPE_PIN)))
        val (reopenKey, reopenCreates) = keys.last()
        assertFalse(reopenCreates)
        assertArrayEquals(emptyVaultKey, reopenKey)
        verify(database, never()).wipe()
    }

    @Test
    fun aNormalUnlockIsNotAnErasedVaultSession() {
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)

        val epoch = vault.security.eraseEpoch()

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))

        verify(vault.database, never()).wipe()
        assertEquals(epoch, vault.security.eraseEpoch())
        assertTrue(vault.prefs.contains(BackupManager.LAST_EXPORT_KEY))
    }

    @Test
    fun anUnlockThatOnlyThenCreatesTheErasedDatabaseAlsoChangesTheEpoch() {
        // The wipe PIN's own open failed; the retry is a plain PIN unlock of the new slot.
        val vault = Vault()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(false)
        assertEquals(VaultUnlockResult.DB_ERROR, vault.security.unlockWithPin(pin(WIPE_PIN)))
        val epoch = vault.security.eraseEpoch()
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(WIPE_PIN)))

        assertNotEquals(epoch, vault.security.eraseEpoch())
        assertEquals(WipeStage.CLEANUP, vault.auth.pendingWipe())
    }

    @Test
    fun anErasedDatabaseThatCannotBeRecordedIsClosedAndCreatedAgainByTheNextUnlock() {
        val vault = Vault()
        // The erase itself is written; recording the new database is not.
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenAnswer {
            vault.store.beforeRename = { throw IOException("disk full") }
            true
        }

        assertEquals(VaultUnlockResult.DB_ERROR, vault.security.unlockWithPin(pin(WIPE_PIN)))

        verify(vault.database).reset()
        assertEquals(WipeStage.DATABASE, vault.auth.pendingWipe())
        assertNull(vault.security.copyBackupRootKey())
        vault.store.beforeRename = null
        doReturn(true).`when`(vault.database).unlockDatabase(anyByteArray(), anyBoolean())

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(WIPE_PIN)))
        verify(vault.database, times(2)).unlockDatabase(anyByteArray(), eq(true))
        assertEquals(WipeStage.CLEANUP, vault.auth.pendingWipe())
    }

    @Test
    fun theStartupFinishCannotDeleteTheDatabaseAnUnlockIsCreating() {
        // A recreated activity resolves the startup state again while the wipe PIN's unlock is
        // creating the empty database on another thread.
        val vault = Vault()
        val creating = CountDownLatch(1)
        val release = CountDownLatch(1)
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenAnswer {
            creating.countDown()
            release.await(5, TimeUnit.SECONDS)
            true
        }
        `when`(vault.database.hasEncryptedDatabase()).thenReturn(true)
        var unlocked: VaultUnlockResult? = null
        val unlock = thread { unlocked = vault.security.unlockWithPin(pin(WIPE_PIN)) }
        assertTrue(creating.await(5, TimeUnit.SECONDS))

        val startup = thread { vault.security.completePendingWipe() }
        startup.join(STARTUP_WAIT_MS)
        assertTrue("the finish waits for the unlock", startup.isAlive)
        release.countDown()
        unlock.join()
        startup.join()
        assertEquals(VaultUnlockResult.SUCCESS, unlocked)

        // Only the unlock's own delete of the old database, before creating the new one.
        verify(vault.database).wipe()
        verify(vault.database, never()).wipeIfClosed()
        // The finish ran after the database was recorded: the cleanup, not the database step.
        assertNull(vault.auth.pendingWipe())
    }

    @Test
    fun theStartupFinishRunsOncePerProcess() {
        val vault = Vault()
        assertTrue(vault.auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
        val restarted = vault.restart()
        assertEquals(InitialStatus.AUTH, restarted.resolveInitialStatus())
        verify(vault.database).wipeIfClosed()

        // The activity is recreated (a rotation): the same process resolves the state again.
        assertEquals(InitialStatus.AUTH, restarted.resolveInitialStatus())

        verify(vault.database).wipeIfClosed()
    }

    @Test
    fun aKeyLeftByAnEraseThatNeverCommittedIsDeletedAtTheNextStart() {
        val vault = Vault()
        // Phase 1 made key B, then the process died before phase 2 recorded it.
        vault.factor.reset(PinKeySlot.B)
        `when`(vault.database.hasEncryptedDatabase()).thenReturn(true)

        assertEquals(InitialStatus.AUTH, vault.restart().resolveInitialStatus())

        assertFalse(vault.factor.hasKey(PinKeySlot.B))
        assertTrue(vault.factor.hasKey(PinKeySlot.A))
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))
    }

    @Test
    fun theStartupKeepsTheKeyOfAnEraseThatIsPending() {
        val vault = Vault()
        assertTrue(vault.auth.unlockWithPin(pin(WIPE_PIN)) is PinUnlock.Erased)
        assertTrue(vault.auth.markWipeDatabaseCreated())
        `when`(vault.database.hasEncryptedDatabase()).thenReturn(true)

        assertEquals(InitialStatus.AUTH, vault.restart().resolveInitialStatus())

        // B is the active key now; the retired one, A, went with the cleanup.
        assertTrue(vault.factor.hasKey(PinKeySlot.B))
        assertFalse(vault.factor.hasKey(PinKeySlot.A))
    }

    @Test
    fun startupAddsTheDecoyToAnExistingVault() {
        val vault = Vault(withWipePin = false)
        vault.store.edit {
            remove(WIPE_SLOT)
            remove(KEY_SLOT)
        }
        val before = vault.store.getBytes(PIN_SLOT)!!
        `when`(vault.database.hasEncryptedDatabase()).thenReturn(true)

        assertEquals(InitialStatus.AUTH, vault.restart().resolveInitialStatus())

        assertArrayEquals(before, vault.store.getBytes(PIN_SLOT))
        assertNotNull(vault.store.getBytes(WIPE_SLOT))
    }

    @Test
    fun theWipePinCanOnlyBeChangedInAnOpenVault() {
        val vault = Vault(withWipePin = false)
        `when`(vault.database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)

        assertEquals(WipePinChange.FAILED, vault.security.configureWipePin(pin(WIPE_PIN)))
        assertFalse(vault.security.removeWipePin())

        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(pin(PIN)))
        val typed = pin(WIPE_PIN)
        assertEquals(WipePinChange.SET, vault.security.configureWipePin(typed))
        assertTrue(typed.all { it == '\u0000' })
        assertTrue(vault.security.removeWipePin())
    }

    /** A committed vault with a recovery phrase, the PIN, a fingerprint and (by default) a wipe PIN. */
    private inner class Vault(withWipePin: Boolean = true) {
        val store = this@WipePinTest.store
        val factor = this@WipePinTest.factor
        val auth = this@WipePinTest.auth
        val prefs = InMemorySharedPreferences()
        val seed = SeedPhraseManager(store)
        val database: SecureDatabaseManager = mock(SecureDatabaseManager::class.java)
        val biometric: BiometricManager = mock(BiometricManager::class.java)
        val security = SecurityManager(prefs, store, seed, database, auth, biometric)

        init {
            assertTrue(seed.initializeVault(seed.generateMnemonic().chars))
            val bootstrap = seed.copyBootstrapKey()!!
            assertTrue(auth.configurePin(pin(PIN), bootstrap))
            if (withWipePin) assertEquals(WipePinChange.SET, auth.configureWipePin(pin(WIPE_PIN)))
            prefs.edit()
                .putBoolean("onboarding_shown_v3", true)
                .putLong(BackupManager.LAST_EXPORT_KEY, 1L)
                .commit()
        }

        /** The same files read by a new process. */
        fun restart() = SecurityManager(prefs, store, seed, database, auth, biometric)
    }

    private fun thread(block: () -> Unit): Thread = Thread(block).apply { start() }

    private fun configured(withWipePin: Boolean): AuthenticationManager {
        val manager = AuthenticationManager(tempVaultStore(), FakePinFactor(), FakeLockoutClock())
        assertTrue(manager.configurePin(pin(PIN), dek))
        if (withWipePin) assertEquals(WipePinChange.SET, manager.configureWipePin(pin(WIPE_PIN)))
        return manager
    }

    private fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    /** Every key and the length of its value. */
    private fun shape(store: VaultStore): Map<String, Int> = keysOf(store).associateWith { store.getBytes(it)!!.size }

    private fun keysOf(store: VaultStore): Set<String> = store.keys().toSortedSet()

    private fun pin(value: String) = value.toCharArray()

    private companion object {
        const val PIN = "135790"
        const val WIPE_PIN = "864200"
        const val DEK_LENGTH = 32
        const val SALT_SIZE = 16
        const val NANOS_PER_MILLI = 1_000_000L
        const val TIMING_SLACK_MS = 250L
        const val STARTUP_WAIT_MS = 300L
        const val PIN_SLOT = "pin.slot"
        const val WIPE_SLOT = "pin.wipe.slot"
        const val KEY_SLOT = "pin.key_slot"
    }
}
