package com.pasich.encly.data.importer

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupTag
import com.pasich.encly.data.backup.BackupImporter
import com.pasich.encly.data.backup.ImportMode
import com.pasich.encly.data.backup.TagMatch
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The batched merge is the backup MERGE, and all or nothing however it ends. */
class ImportMergerTest {
    private fun tag(n: Int, name: String = "Tag $n") =
        BackupTag(uid = "tag-$n", name = name, visible = true, position = n)

    private fun note(n: Int, vararg tags: Int, archived: Boolean = false) = BackupNote(
        uid = "note-$n",
        title = "Note $n",
        value = "[]",
        description = "",
        date = 1_000L + n,
        dateCreate = 500L + n,
        tagUids = tags.map { "tag-$it" },
        isTrash = n % 3 == 0,
        isPinned = archived,
        isArchived = archived,
    )

    private fun payload(notes: Int, tags: List<BackupTag> = listOf(tag(1), tag(2), tag(3))) = BackupPayload(
        exportedAt = 1L,
        tags = tags,
        notes = (1..notes).map { note(it, (it % 3) + 1, ((it + 1) % 3) + 1) },
        tasks = emptyList(),
    )

    private fun InMemoryVaultDataStore.withVault() = apply {
        runBlocking {
            insertTag(Tag(nameTag = "TAG 1", position = 4, uid = "mine-1"))
            insertTag(Tag(nameTag = "Other", position = 7, uid = "mine-2"))
            insertNote(Note(title = "Mine", uid = "mine"))
            insertNote(Note(title = "Imported before", uid = "note-2"))
        }
    }

    private fun InMemoryVaultDataStore.state() =
        listOf(notes.toList(), tags.toList(), tasks.toList(), subtasks.toList(), noteTags.toList())

    @Test
    fun givesTheSameVaultAndSummaryAsTheBackupMergeWhateverTheBatchSize() {
        val data = payload(23, tags = listOf(tag(1), tag(2), tag(3), tag(4, name = "Other")))
        val expected = InMemoryVaultDataStore().withVault()
        val summary = runBlocking { BackupImporter.import(data, ImportMode.MERGE, expected, TagMatch.UID_OR_NAME) }

        for (batch in listOf(1, 2, 5, 23, 200)) {
            val vault = InMemoryVaultDataStore().withVault()
            val result = runBlocking { ImportMerger.merge(data, vault, batch) }

            assertEquals("batch $batch", expected.state(), vault.state())
            assertEquals(summary.notesAdded, result.notesAdded)
            assertEquals(summary.tagsAdded, result.tagsAdded)
            assertEquals(summary.skipped, result.skipped)
        }
        // "TAG 1" and "Other" were the vault's: matched by name, not added again; note-2 was skipped by uid.
        assertEquals(listOf("TAG 1", "Other", "Tag 2", "Tag 3"), expected.tags.map { it.nameTag })
        assertEquals(22, summary.notesAdded)
        assertEquals(2, summary.tagsAdded)
    }

    @Test
    fun anArchivedNoteIsNeverPinned() {
        val vault = InMemoryVaultDataStore()
        runBlocking { ImportMerger.merge(payload(1).copy(notes = listOf(note(1, archived = true))), vault) }
        assertTrue(vault.notes.single().isArchived)
        assertFalse(vault.notes.single().isPinned)
    }

    @Test
    fun theBatchSizeMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ImportMerger.merge(payload(1), InMemoryVaultDataStore(), batchSize = 0) }
        }
    }

    @Test
    fun aFailureInALaterBatchLeavesTheVaultAsItWas() {
        val faulty = FaultyVault().apply { vault.withVault() }
        val before = faulty.vault.state()
        faulty.beforeNote = { n -> if (n == 9) throw IllegalStateException("database or disk is full") }

        assertThrows(IllegalStateException::class.java) { runBlocking { ImportMerger.merge(payload(20), faulty, 4) } }

        assertEquals(before, faulty.vault.state())
    }

    @Test
    fun aFailureAtCommitLeavesTheVaultAsItWas() {
        val faulty = FaultyVault().apply { vault.withVault() }
        val before = faulty.vault.state()
        faulty.failCommit = IOException("No space left on device")

        assertThrows(IOException::class.java) { runBlocking { ImportMerger.merge(payload(20), faulty, 4) } }

        assertEquals(before, faulty.vault.state())
        // The disk frees up: the same import, run again, is complete.
        faulty.failCommit = null
        runBlocking { ImportMerger.merge(payload(20), faulty, 4) }
        assertEquals(21, faulty.vault.notes.size)
    }

    @Test
    fun aKilledProcessLeavesNothingBehind() {
        // What a crash is to the vault: the block ends with something that no handler recovers from.
        class ProcessKilled : Error("simulated")

        val faulty = FaultyVault().apply { vault.withVault() }
        val before = faulty.vault.state()
        faulty.beforeNote = { n -> if (n == 13) throw ProcessKilled() }

        assertThrows(ProcessKilled::class.java) { runBlocking { ImportMerger.merge(payload(30), faulty, 5) } }

        assertEquals(before, faulty.vault.state())
    }

    @Test
    fun outOfMemoryInTheMiddleLeavesNothingBehind() {
        val faulty = FaultyVault().apply { vault.withVault() }
        val before = faulty.vault.state()
        faulty.beforeNote = { n -> if (n == 6) throw OutOfMemoryError("simulated") }

        assertThrows(OutOfMemoryError::class.java) { runBlocking { ImportMerger.merge(payload(30), faulty, 5) } }

        assertEquals(before, faulty.vault.state())
    }

    @Test
    fun aCancelStopsAtTheNextBatchAndRollsBack() {
        val faulty = FaultyVault().apply { vault.withVault() }
        val before = faulty.vault.state()
        val inserted = ArrayList<Int>()
        faulty.beforeNote = { n -> inserted += n }
        // The cancel arrives in the middle of the first batch of five: that batch is finished, the next not begun.
        val outcome = runCatching {
            runBlocking { async { ImportMerger.merge(payload(30), CancellingVault(faulty, atNote = 3), 5) }.await() }
        }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(before, faulty.vault.state())
        assertTrue("the merge ran on after the cancel", inserted.size < 10)
    }

    /** Cancels the coroutine that is merging when note [atNote] is inserted. */
    private class CancellingVault(private val inner: FaultyVault, private val atNote: Int) :
        VaultDataStore by inner {
        private var count = 0

        override fun bindToCurrentSession(): VaultDataStore = this

        override suspend fun insertNote(note: Note): Long {
            if (++count == atNote) currentCoroutineContext().cancel()
            return inner.insertNote(note)
        }

        override suspend fun <R> inTransaction(block: suspend () -> R): R = inner.inTransaction { block() }
    }
}
