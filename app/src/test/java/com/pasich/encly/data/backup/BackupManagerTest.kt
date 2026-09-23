package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
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

    private companion object {
        const val ROOT_LENGTH = 32
    }
}
