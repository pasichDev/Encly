package com.pasich.encly.data.backup

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.backup.assertError
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupImporterTest {

    private val words = MnemonicCode(WordCount.COUNT_12).chars

    /** Every block type the editor can produce, serialized as the notes table stores it. */
    private val allBlocks = BlockConverter.blocksToJson(
        listOf(
            Block.TextBlock(MutableStateFlow("Plain text")),
            Block.HBlock(MutableStateFlow("H1"), BlockType.H1),
            Block.HBlock(MutableStateFlow("H2"), BlockType.H2),
            Block.HBlock(MutableStateFlow("H3"), BlockType.H3),
            Block.HBlock(MutableStateFlow("H4"), BlockType.H4),
            Block.QuoteBlock(MutableStateFlow("Quote")),
            Block.SeparatorBlock(),
            Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "Site", url = "https://example.org"))),
            Block.ListBlock(
                MutableStateFlow(listOf(ItemListBlock("done", isCheck = true), ItemListBlock("open"))),
                BlockType.LIST_CHECK,
            ),
            Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("first"))), BlockType.LIST_NUMBER),
        ),
    )

    private suspend fun seededVault() = InMemoryVaultDataStore().apply {
        val work = insertTag(Tag(nameTag = "Work", isVisible = true, position = 0, uid = "tag-work"))
        insertTag(Tag(nameTag = "Hidden", isVisible = false, position = 1, uid = "tag-hidden"))
        insertNote(
            Note(
                title = "Plan",
                value = allBlocks,
                description = "d",
                date = 20,
                dateCreate = 10,
                tagId = work,
                uid = "n-plan",
            ),
        )
        insertNote(Note(title = "Old", value = "[]", date = 5, dateCreate = 1, isTrash = true, uid = "n-old"))
        insertTask(
            Task(
                title = "Call",
                description = "about it",
                createdDate = 100,
                priority = 2,
                categoryId = work,
                position = 3,
                uid = "k-call",
            ),
        )
        insertTask(Task(title = "Done", isCompleted = true, createdDate = 1, completedDate = 2, uid = "k-done"))
    }

    /** Export exactly as the app does: snapshot -> payload -> JSON -> sealed file. */
    private suspend fun export(store: InMemoryVaultDataStore): ByteArray {
        val plaintext = BackupPayloadCodec.encode(BackupMapper.toPayload(store.snapshot(), exportedAt = 42))
        return BackupCipher.seal(plaintext, BackupSecret.RecoveryPhrase(words.copyOf()))
    }

    private fun open(file: ByteArray): BackupPayload =
        BackupPayloadCodec.decode(BackupCipher.open(file, BackupSecret.RecoveryPhrase(words.copyOf())))

    @Test
    fun exportThenImportOnAFreshVaultRestoresEverything() = runTest {
        val source = seededVault()
        val restored = InMemoryVaultDataStore()

        val summary = BackupImporter.import(open(export(source)), ImportMode.REPLACE, restored)

        assertEquals(ImportSummary(notesAdded = 2, tagsAdded = 2, tasksAdded = 2, skipped = 0), summary)
        assertEquals(portable(source), portable(restored))
        assertEquals(allBlocks, restored.notes.single { it.uid == "n-plan" }.value)
    }

    @Test
    fun emptyVaultRoundTrips() = runTest {
        val restored = InMemoryVaultDataStore()

        val payload = open(export(InMemoryVaultDataStore()))
        val summary = BackupImporter.import(payload, ImportMode.REPLACE, restored)

        assertEquals(42L, payload.exportedAt)
        assertEquals(ImportSummary(0, 0, 0, 0), summary)
        assertTrue(restored.notes.isEmpty() && restored.tags.isEmpty() && restored.tasks.isEmpty())
    }

    @Test
    fun mergeSkipsRecordsWhoseUidIsAlreadyThere() = runTest {
        val payload = open(export(seededVault()))
        val target = InMemoryVaultDataStore().apply {
            insertTag(Tag(nameTag = "Mine", position = 0, uid = "tag-mine"))
            insertTag(Tag(nameTag = "Work (renamed)", position = 1, uid = "tag-work"))
            insertNote(Note(title = "Plan (edited here)", value = "[]", uid = "n-plan"))
            insertTask(Task(title = "Local task", uid = "k-local"))
        }

        val summary = BackupImporter.import(payload, ImportMode.MERGE, target)

        assertEquals(ImportSummary(notesAdded = 1, tagsAdded = 1, tasksAdded = 2, skipped = 2), summary)
        // Local versions win; nothing that existed is overwritten or removed.
        assertEquals("Plan (edited here)", target.notes.single { it.uid == "n-plan" }.title)
        assertEquals("Work (renamed)", target.tags.single { it.uid == "tag-work" }.nameTag)
        assertTrue(target.tasks.any { it.uid == "k-local" })
        // Imported links resolve to this device's row of the shared tag.
        val localWork = target.tags.single { it.uid == "tag-work" }.id
        assertEquals(localWork, target.tasks.single { it.uid == "k-call" }.categoryId)
        // Merged tags go after the device's own tags.
        assertEquals(2, target.tags.single { it.uid == "tag-hidden" }.position)
    }

    @Test
    fun mergingTheSameBackupTwiceAddsNothing() = runTest {
        val payload = open(export(seededVault()))
        val target = InMemoryVaultDataStore()
        BackupImporter.import(payload, ImportMode.MERGE, target)

        val second = BackupImporter.import(payload, ImportMode.MERGE, target)

        assertEquals(ImportSummary(0, 0, 0, skipped = 6), second)
        assertEquals(2, target.notes.size)
    }

    @Test
    fun replaceDeletesTheVaultFirst() = runTest {
        val payload = open(export(seededVault()))
        val target = InMemoryVaultDataStore().apply {
            insertTag(Tag(nameTag = "Mine", uid = "tag-mine"))
            insertNote(Note(title = "Plan (edited here)", uid = "n-plan"))
            insertTask(Task(title = "Local task", uid = "k-local"))
        }

        BackupImporter.import(payload, ImportMode.REPLACE, target)

        assertEquals(setOf("n-plan", "n-old"), target.notes.map { it.uid }.toSet())
        assertEquals("Plan", target.notes.single { it.uid == "n-plan" }.title)
        assertEquals(setOf("tag-work", "tag-hidden"), target.tags.map { it.uid }.toSet())
        assertEquals(setOf("k-call", "k-done"), target.tasks.map { it.uid }.toSet())
    }

    @Test
    fun aFailureHalfwayLeavesTheVaultUntouched() = runTest {
        val payload = open(export(seededVault()))
        listOf(ImportMode.MERGE, ImportMode.REPLACE).forEach { mode ->
            val target = InMemoryVaultDataStore().apply {
                insertNote(Note(title = "Keep me", uid = "n-keep"))
            }
            val before = portable(target)
            target.failOnInsert = 1 + 4 // the seeded note, then the 4th insert of the import

            try {
                BackupImporter.import(payload, mode, target)
                fail("import should have failed")
            } catch (_: IllegalStateException) {
                // expected
            }

            assertEquals(before, portable(target))
        }
    }

    @Test
    fun wrongWordsNeverReachTheVault() = runTest {
        val file = export(seededVault())
        val other = MnemonicCode(WordCount.COUNT_12).chars

        assertError(BackupError.WRONG_SECRET) { BackupCipher.open(file, BackupSecret.RecoveryPhrase(other)) }
    }

    /** Vault content without the device-local autoincrement ids: links become tag uids. */
    private fun portable(store: InMemoryVaultDataStore): Any {
        val tagUid = store.tags.associate { it.id to it.uid }
        return Triple(
            store.tags.map { listOf(it.uid, it.nameTag, it.isVisible, it.position) }.toSet(),
            store.notes.map {
                listOf(
                    it.uid,
                    it.title,
                    it.value,
                    it.description,
                    it.date,
                    it.dateCreate,
                    tagUid[it.tagId],
                    it.isTrash,
                )
            }.toSet(),
            store.tasks.map {
                listOf(
                    it.uid, it.title, it.description, it.isCompleted, it.createdDate, it.completedDate,
                    it.priority, tagUid[it.categoryId], it.position,
                )
            }.toSet(),
        )
    }
}
