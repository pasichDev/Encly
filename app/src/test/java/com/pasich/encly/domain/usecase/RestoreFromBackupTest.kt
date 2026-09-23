package com.pasich.encly.domain.usecase

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupMapper
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Onboarding's "Restore from backup": decrypt first, create the vault only on success. */
class RestoreFromBackupTest {

    private val words = MnemonicCode(WordCount.COUNT_12).chars
    private lateinit var security: SecurityManager
    private lateinit var store: InMemoryVaultDataStore
    private lateinit var pending: PendingRestore
    private lateinit var useCase: OnboardingUseCase
    private var vaultSeed: String? = null

    private fun anyChars(): CharArray = ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)

    @Before
    fun setUp() {
        security = mock(SecurityManager::class.java)
        `when`(security.isValidRecoveryPhrase(anyChars())).thenAnswer {
            runCatching { MnemonicCode(it.getArgument<CharArray>(0).copyOf()).validate() }.isSuccess
        }
        `when`(security.initializeNewVault(anyChars())).thenAnswer {
            // Copied now: the use case wipes the phrase once the vault exists.
            vaultSeed = String(it.getArgument<CharArray>(0))
            true
        }
        store = InMemoryVaultDataStore()
        val backupManager = BackupManager(security, store, InMemorySharedPreferences())
        pending = PendingRestore(backupManager)
        useCase = OnboardingUseCase(security, backupManager, pending)
    }

    private suspend fun backupFile(): ByteArray {
        val source = InMemoryVaultDataStore().apply {
            val tag = insertTag(Tag(nameTag = "Work", uid = "tag-work"))
            insertNote(Note(title = "Plan", value = "[]", tagId = tag, uid = "n-plan"))
        }
        val plaintext = BackupPayloadCodec.encode(BackupMapper.toPayload(source.snapshot(), exportedAt = 1))
        return BackupCipher.seal(plaintext, BackupSecret.RecoveryPhrase(words.copyOf()))
    }

    @Test
    fun rightWordsCreateTheVaultAroundThemAndRestoreAfterSetup() = runTest {
        val typed = ("  " + String(words).uppercase().replace(" ", "  ") + "\n").toCharArray()

        val result = useCase.restoreFromBackup(backupFile(), typed)

        assertTrue(result.isSuccess)
        assertEquals(String(words), vaultSeed)
        assertTrue("typed words are wiped", typed.all { it == 0.toChar() })
        assertTrue("nothing is written before PIN setup", store.notes.isEmpty())

        assertTrue(pending.apply())
        assertEquals(listOf("n-plan"), store.notes.map { it.uid })
        assertEquals(store.tags.single().id, store.notes.single().tagId)
    }

    @Test
    fun wrongWordsCreateNothing() = runTest {
        val result = useCase.restoreFromBackup(backupFile(), MnemonicCode(WordCount.COUNT_12).chars)

        assertEquals(BackupError.WRONG_SECRET, (result.exceptionOrNull() as BackupException).error)
        verify(security, never()).initializeNewVault(anyChars())
        assertTrue(pending.apply())
        assertTrue(store.notes.isEmpty())
    }

    @Test
    fun invalidPhraseIsRejectedBeforeDecrypting() = runTest {
        val result = useCase.restoreFromBackup(backupFile(), "not twelve words".toCharArray())

        assertEquals(BackupError.INVALID_PHRASE, (result.exceptionOrNull() as BackupException).error)
        verify(security, never()).initializeNewVault(anyChars())
    }

    @Test
    fun tamperedFileCreatesNothing() = runTest {
        val file = backupFile().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

        val result = useCase.restoreFromBackup(file, words.copyOf())

        assertEquals(BackupError.WRONG_SECRET, (result.exceptionOrNull() as BackupException).error)
        verify(security, never()).initializeNewVault(anyChars())
    }
}
