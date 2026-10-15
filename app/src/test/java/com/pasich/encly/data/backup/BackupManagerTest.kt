package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.model.Note
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

class BackupManagerTest {

    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var manager: BackupManager

    @Before
    fun setUp() = runTest {
        val security = mock(SecurityManager::class.java)
        // A fresh copy per call: createBackup wipes the key it was handed.
        `when`(security.copyBackupRootKey()).thenAnswer { ByteArray(ROOT_LENGTH) { 5 } }
        val store = InMemoryVaultDataStore().apply {
            insertNote(Note(title = "Plan", value = "[]", uid = "n-plan"))
        }
        prefs = InMemorySharedPreferences()
        manager = BackupManager(security, store, prefs).apply { clock = { 1L } }
    }

    @Test
    fun aBackupExactlyAtTheImportLimitIsWrittenAndReadsBack() = runTest {
        val size = manager.createBackup().size
        manager.maxFileBytes = size

        val file = manager.createBackup()

        assertEquals(size, file.size)
        val read = BackupDocuments.readCapped(ByteArrayInputStream(file), limit = size)
        assertEquals(size, BackupFormat.parse(read).file.size)
    }

    @Test
    fun aVaultTooLargeToImportIsRefusedInsteadOfExported() = runTest {
        val size = manager.createBackup().size
        manager.maxFileBytes = size - 1

        try {
            manager.createBackup()
            fail("expected TOO_LARGE")
        } catch (e: BackupException) {
            assertEquals(BackupError.TOO_LARGE, e.error)
        }
        // Nothing is recorded as exported.
        assertNull(manager.lastExportAt())
    }

    @Test
    fun theDefaultExportLimitIsTheImportLimit() {
        assertEquals(BackupFormat.MAX_FILE_BYTES, manager.maxFileBytes)
    }

    @Test
    fun exportRefusesAStoreWhoseSessionClosedAfterSnapshot() = runTest {
        val security = mock(SecurityManager::class.java)
        `when`(security.copyBackupRootKey()).thenAnswer { ByteArray(ROOT_LENGTH) { 5 } }
        val memory = InMemoryVaultDataStore()
        var active = true
        val bound = object : VaultDataStore by memory {
            override suspend fun snapshot(): VaultSnapshot = memory.snapshot().also { active = false }

            override fun assertCurrentSession() {
                if (!active) error("session closed")
            }
        }
        val store = object : VaultDataStore by memory {
            override fun bindToCurrentSession(): VaultDataStore = bound
        }

        try {
            BackupManager(security, store, InMemorySharedPreferences()).createBackup()
            fail("expected a closed-session failure")
        } catch (e: BackupException) {
            assertEquals(BackupError.IO, e.error)
        }
    }

    @Test
    fun theDecoyVaultRefusesEveryBackupStepAsAStorageError() = runTest {
        val security = mock(SecurityManager::class.java)
        `when`(security.isDecoySession()).thenReturn(true)
        val decoy = BackupManager(security, InMemoryVaultDataStore(), InMemorySharedPreferences())
        val empty = BackupPayload(exportedAt = 0, tags = emptyList(), notes = emptyList(), tasks = emptyList())

        val errors = listOf(
            runCatching { decoy.decrypt(ByteArray(1), "words".toCharArray()) },
            runCatching { decoy.import(empty, ImportMode.MERGE) },
            runCatching { decoy.createBackup() },
        ).map { (it.exceptionOrNull() as BackupException).error }

        assertEquals(List(3) { BackupError.IO }, errors)
    }

    private companion object {
        const val ROOT_LENGTH = 32
    }
}
