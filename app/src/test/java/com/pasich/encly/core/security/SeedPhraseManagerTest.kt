package com.pasich.encly.core.security

import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupKeys
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SeedPhraseManagerTest {

    private lateinit var file: File
    private lateinit var manager: SeedPhraseManager

    @Before
    fun setUp() {
        file = tempVaultFile()
        manager = SeedPhraseManager(VaultStore(file))
    }

    @Test
    fun recoverySeedUnwrapsTheBootstrapDek() {
        val phrase = manager.generateMnemonic().chars
        assertTrue(manager.initializeVault(phrase.copyOf()))
        val bootstrap = manager.copyBootstrapKey()!!

        assertTrue(manager.hasRecoverySeed())
        assertArrayEquals(bootstrap, manager.unlockWithSeed(phrase.copyOf()))
    }

    @Test
    fun wrongSeedNeverUnwraps() {
        assertTrue(manager.initializeVault(manager.generateMnemonic().chars))

        assertNull(manager.unlockWithSeed(manager.generateMnemonic().chars))
    }

    @Test
    fun autoManagedVaultHasNoRecoverySlot() {
        assertTrue(manager.initializeVault(null))

        assertFalse(manager.hasRecoverySeed())
        assertTrue(manager.verificationKeyData())
        assertTrue(manager.copyBootstrapKey()!!.size == 32)
    }

    @Test
    fun bootstrapKeyIsGoneOnceCleared() {
        assertTrue(manager.initializeVault(null))

        manager.clearBootstrapKey()

        assertNull(manager.copyBootstrapKey())
    }

    // --- backup key -----------------------------------------------------------------------

    @Test
    fun backupSealedInTheVaultOpensWithOnlyTheWordsOnAFreshInstall() {
        val phrase = manager.generateMnemonic().chars
        assertTrue(manager.initializeVault(phrase.copyOf()))
        val dek = manager.copyBootstrapKey()!!
        assertTrue(manager.hasBackupKey())

        val root = manager.unwrapBackupRoot(dek)!!
        val file = BackupCipher.seal("payload".toByteArray(), BackupSecret.RecoveryRoot(root))

        // A new phone has nothing but the file and the 12 words.
        val opened = BackupCipher.open(file, BackupSecret.RecoveryPhrase(phrase.copyOf()))
        assertArrayEquals("payload".toByteArray(), opened)
    }

    @Test
    fun backupKeyNeedsTheVaultKeyAndDiffersFromIt() {
        assertTrue(manager.initializeVault(manager.generateMnemonic().chars))
        val dek = manager.copyBootstrapKey()!!

        assertNull(manager.unwrapBackupRoot(ByteArray(32)))
        assertFalse(dek.contentEquals(manager.unwrapBackupRoot(dek)))
    }

    @Test
    fun autoManagedVaultHasNoBackupKeyUntilARecoverySeedIsAdded() {
        assertTrue(manager.initializeVault(null))
        val dek = manager.copyBootstrapKey()!!
        assertFalse(manager.hasBackupKey())
        assertNull(manager.unwrapBackupRoot(dek))

        val phrase = manager.generateMnemonic().chars
        assertTrue(manager.addRecoverySeed(phrase.copyOf(), dek))

        assertTrue(manager.hasRecoverySeed())
        assertTrue(manager.hasBackupKey())
        assertArrayEquals(dek, manager.unlockWithSeed(phrase.copyOf()))
        assertArrayEquals(BackupKeys.rootFromMnemonic(phrase.copyOf()), manager.unwrapBackupRoot(dek))
        // A second seed is refused: it would silently replace the one the user wrote down.
        assertFalse(manager.addRecoverySeed(manager.generateMnemonic().chars, dek))
    }

    @Test
    fun backupKeyIsOnlyRecreatedFromThisVaultsOwnWords() {
        val phrase = manager.generateMnemonic().chars
        assertTrue(manager.initializeVault(phrase.copyOf()))
        val dek = manager.copyBootstrapKey()!!

        assertFalse(manager.createBackupKey(manager.generateMnemonic().chars, dek))
        assertTrue(manager.createBackupKey(phrase.copyOf(), dek))
    }

    // --- replacing the recovery phrase ----------------------------------------------------

    @Test
    fun replacingThePhraseMovesTheVaultToTheNewWordsOnly() {
        val old = manager.generateMnemonic().chars
        assertTrue(manager.initializeVault(old.copyOf()))
        val dek = manager.copyBootstrapKey()!!
        val oldRoot = manager.unwrapBackupRoot(dek)!!
        val new = manager.generateMnemonic().chars

        assertTrue(manager.replaceRecoverySeed(new.copyOf(), dek))

        assertArrayEquals(dek, manager.unlockWithSeed(new.copyOf()))
        assertNull("the old words open nothing any more", manager.unlockWithSeed(old.copyOf()))
        val newRoot = manager.unwrapBackupRoot(dek)!!
        assertArrayEquals(BackupKeys.rootFromMnemonic(new.copyOf()), newRoot)
        assertFalse(oldRoot.contentEquals(newRoot))
        // And it is what a fresh process reads back.
        val reloaded = SeedPhraseManager(VaultStore(file))
        assertArrayEquals(dek, reloaded.unlockWithSeed(new.copyOf()))
        assertNull(reloaded.unlockWithSeed(old.copyOf()))
    }

    @Test
    fun backupsMadeBeforeAReplacementStillOpenOnlyWithTheOldWords() {
        val old = manager.generateMnemonic().chars
        assertTrue(manager.initializeVault(old.copyOf()))
        val dek = manager.copyBootstrapKey()!!
        val before = BackupCipher.seal("old".toByteArray(), BackupSecret.RecoveryRoot(manager.unwrapBackupRoot(dek)!!))
        val new = manager.generateMnemonic().chars

        assertTrue(manager.replaceRecoverySeed(new.copyOf(), dek))
        val after = BackupCipher.seal("new".toByteArray(), BackupSecret.RecoveryRoot(manager.unwrapBackupRoot(dek)!!))

        assertArrayEquals("old".toByteArray(), BackupCipher.open(before, BackupSecret.RecoveryPhrase(old.copyOf())))
        assertArrayEquals("new".toByteArray(), BackupCipher.open(after, BackupSecret.RecoveryPhrase(new.copyOf())))
        assertTrue(runCatching { BackupCipher.open(before, BackupSecret.RecoveryPhrase(new.copyOf())) }.isFailure)
    }

    @Test
    fun replacingNeedsAnExistingPhraseAndAValidNewOne() {
        assertTrue(manager.initializeVault(null))
        val dek = manager.copyBootstrapKey()!!
        assertFalse(manager.replaceRecoverySeed(manager.generateMnemonic().chars, dek))

        assertTrue(manager.addRecoverySeed(manager.generateMnemonic().chars, dek))
        assertFalse(manager.replaceRecoverySeed("not a valid phrase".toCharArray(), dek))
        assertFalse(manager.replaceRecoverySeed(manager.generateMnemonic().chars, ByteArray(16)))
    }

    @Test
    fun theRecoveryKeyAndTheBackupRootNormaliseThePhraseTheSameWay() {
        val phrase = manager.generateMnemonic().chars
        assertTrue(manager.initializeVault(phrase.copyOf()))
        val dek = manager.copyBootstrapKey()!!
        val messy = ("  " + String(phrase).uppercase().replace(" ", " \t ") + "\n").toCharArray()

        assertArrayEquals(dek, manager.unlockWithSeed(messy.copyOf()))
        assertArrayEquals(BackupKeys.rootFromMnemonic(messy.copyOf()), manager.unwrapBackupRoot(dek))
    }
}
