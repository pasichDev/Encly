package com.pasich.encly.core.security

import android.content.Context
import android.util.Base64
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupKeys
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.anyByteArray
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

class SeedPhraseManagerTest {

    private lateinit var base64: MockedStatic<Base64>
    private lateinit var manager: SeedPhraseManager

    @Before
    fun setUp() {
        // android.util.Base64 is a stub on the JVM; route it to java.util.Base64.
        base64 = mockStatic(Base64::class.java)
        base64.`when`<String> { Base64.encodeToString(anyByteArray(), anyInt()) }
            .thenAnswer { java.util.Base64.getEncoder().encodeToString(it.getArgument(0)) }
        base64.`when`<ByteArray> { Base64.decode(anyString(), anyInt()) }
            .thenAnswer { java.util.Base64.getDecoder().decode(it.getArgument<String>(0)) }

        val context = mock(Context::class.java)
        val prefs = InMemorySharedPreferences()
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        manager = SeedPhraseManager(context)
    }

    @After
    fun tearDown() {
        base64.close()
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
}
