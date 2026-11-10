package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.model.Note
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

class BackupManagerTest {

    private lateinit var prefs: InMemoryKeyValueStore
    private lateinit var manager: BackupManager
    private lateinit var security: SecurityManager
    private val preferences = FakePreferences()

    @Before
    fun setUp() = runTest {
        security = mock(SecurityManager::class.java)
        // A fresh copy per call: createBackup wipes the key it was handed.
        `when`(security.copyBackupRootKey()).thenAnswer { ByteArray(ROOT_LENGTH) { 5 } }
        val store = InMemoryVaultDataStore().apply {
            insertNote(Note(title = "Plan", value = "[]", uid = "n-plan"))
        }
        preferences.notes = { store.notes.map { it.uid } }
        prefs = InMemoryKeyValueStore()
        manager = BackupManager(security, store, prefs, preferences).apply { clock = { 1L } }
    }

    @Test
    fun aBackupExactlyAtTheImportLimitIsWrittenAndReadsBack() = runTest {
        val size = manager.createBackup().size
        manager.maxFileBytes = size

        val file = manager.createBackup()

        assertEquals(size, file.size)
        val read = BackupDocuments.readCapped(ByteArrayInputStream(file).asSource(), limit = size)
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
            BackupManager(security, store, InMemoryKeyValueStore(), NoBackupPreferences).createBackup()
            fail("expected a closed-session failure")
        } catch (e: BackupException) {
            assertEquals(BackupError.IO, e.error)
        }
    }

    @Test
    fun theDecoyVaultRefusesEveryBackupStepAsAStorageError() = runTest {
        val security = mock(SecurityManager::class.java)
        `when`(security.isDecoySession()).thenReturn(true)
        val decoy = BackupManager(security, InMemoryVaultDataStore(), InMemoryKeyValueStore(), NoBackupPreferences)
        val empty = BackupPayload(exportedAt = 0, tags = emptyList(), notes = emptyList(), tasks = emptyList())

        val errors = listOf(
            runCatching { decoy.decrypt(ByteArray(1), "words".toCharArray()) },
            runCatching { decoy.import(empty, ImportMode.MERGE) },
            runCatching { decoy.createBackup() },
        ).map { (it.exceptionOrNull() as BackupException).error }

        assertEquals(List(3) { BackupError.IO }, errors)
    }

    @Test
    fun theBackupCarriesOnlyTheAllowListedPreferences() = runTest {
        preferences.values = mapOf("theme_type" to "DARK", "pin.slot" to "secret", "onboarding_shown_v3" to "true")

        val payload = decrypt(manager.createBackup())

        assertEquals(mapOf("theme_type" to "DARK"), payload.settings)
        assertEquals(BackupPayload.SCHEMA_VERSION, payload.schema)
    }

    @Test
    fun aBackupIsStillMadeWhenThePreferencesCannotBeRead() = runTest {
        preferences.failOnSnapshot = true

        val payload = decrypt(manager.createBackup())

        assertEquals(emptyMap<String, String>(), payload.settings)
        assertEquals(1, payload.notes.size)
    }

    @Test
    fun replaceRestoresPreferencesAfterTheNotes() = runTest {
        val payload = payloadWith(mapOf("theme_type" to "DARK"))

        manager.import(payload, ImportMode.REPLACE)

        assertEquals(listOf(mapOf("theme_type" to "DARK")), preferences.applied)
        assertEquals(listOf(listOf("n-new")), preferences.notesWhenApplied)
    }

    @Test
    fun mergeKeepsThePreferencesOfThisInstall() = runTest {
        manager.import(payloadWith(mapOf("theme_type" to "DARK")), ImportMode.MERGE)

        assertEquals(emptyList<Map<String, String>>(), preferences.applied)
    }

    @Test
    fun aFailedImportRestoresNoPreferences() = runTest {
        val store = InMemoryVaultDataStore().apply { failOnInsert = 1 }
        val failing = BackupManager(security, store, InMemoryKeyValueStore(), preferences)

        val result = runCatching { failing.import(payloadWith(mapOf("theme_type" to "DARK")), ImportMode.REPLACE) }

        assertEquals(BackupError.IO, (result.exceptionOrNull() as BackupException).error)
        assertEquals(emptyList<Map<String, String>>(), preferences.applied)
    }

    @Test
    fun aBackupWithoutPreferencesRestoresAsBefore() = runTest {
        val summary = manager.import(payloadWith(emptyMap()), ImportMode.REPLACE)

        assertEquals(1, summary.notesAdded)
        assertEquals(emptyList<Map<String, String>>(), preferences.applied)
    }

    @Test
    fun preferencesThatCannotBeAppliedNeverFailTheRestore() = runTest {
        preferences.failOnApply = true

        val summary = manager.import(payloadWith(mapOf("theme_type" to "DARK")), ImportMode.REPLACE)

        assertEquals(1, summary.notesAdded)
    }

    private fun payloadWith(settings: Map<String, String>) = BackupPayload(
        exportedAt = 0,
        tags = emptyList(),
        notes = listOf(
            BackupNote("n-new", "New", "[]", "", 1L, 1L, isTrash = false),
        ),
        tasks = emptyList(),
        settings = settings,
    )

    private fun decrypt(file: ByteArray): BackupPayload = BackupPayloadCodec.decode(
        BackupCipher.open(file, BackupSecret.RecoveryRoot(ByteArray(ROOT_LENGTH) { 5 })),
    )

    private class FakePreferences : BackupPreferences {
        var values: Map<String, String> = emptyMap()
        var failOnSnapshot = false
        var failOnApply = false
        val applied = mutableListOf<Map<String, String>>()
        val notesWhenApplied = mutableListOf<List<String>>()
        var notes: () -> List<String> = { emptyList() }

        override suspend fun snapshot(): Map<String, String> {
            if (failOnSnapshot) error("unreadable")
            return values
        }

        override suspend fun apply(raw: Map<String, String>) {
            applied += raw
            notesWhenApplied += notes()
            if (failOnApply) error("unwritable")
        }
    }

    private companion object {
        const val ROOT_LENGTH = 32
    }
}
