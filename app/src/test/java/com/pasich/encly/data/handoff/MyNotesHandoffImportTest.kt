package com.pasich.encly.data.handoff

import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.backup.BackupImporter
import com.pasich.encly.data.backup.ImportMode
import com.pasich.encly.data.backup.TagMatch
import com.pasich.encly.data.handoff.HandoffFixtures.category
import com.pasich.encly.data.handoff.HandoffFixtures.handoff
import com.pasich.encly.data.handoff.HandoffFixtures.note
import com.pasich.encly.data.handoff.HandoffFixtures.read
import com.pasich.encly.data.handoff.HandoffFixtures.tag
import com.pasich.encly.data.handoff.HandoffFixtures.task
import com.pasich.encly.data.model.Tag
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Hand-off -> mapper -> the backup MERGE import, against an in-memory vault. */
class MyNotesHandoffImportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun mapped(json: JsonObject): HandoffImport = MyNotesHandoffMapper.map(read(temp.root, json))

    private suspend fun import(json: JsonObject, store: InMemoryVaultDataStore) =
        BackupImporter.import(mapped(json).payload, ImportMode.MERGE, store, TagMatch.UID_OR_NAME)

    private val sample = handoff(
        tags = listOf(tag("mynotes:tag:1", "Work", position = 0), tag("mynotes:tag:2", "Home", position = 1)),
        notes = listOf(
            note("note-a", title = " Plan ", tag = "Work", isPinned = true, attachments = 2),
            note("note-b", tag = "Unknown", isTrash = true, attachments = 1),
        ),
        categories = listOf(category("mynotes:category:1", "Errands")),
        tasks = listOf(
            task("mynotes:task:1", "Buy milk\nand eggs\n", isDone = true, categoryId = "mynotes:category:1"),
            task("mynotes:task:2", "Call"),
            task("mynotes:task:3", "  "),
        ),
    )

    @Test
    fun mapsRecordsAndCountsWhatDoesNotComeOver() {
        val result = mapped(sample)
        assertEquals(HandoffPreview(notes = 2, tasks = 2, tags = 3, attachments = 3, pinned = 1), result.preview)
        assertEquals(1, result.dropped)

        val payload = result.payload
        val plan = payload.notes.first { it.uid == "note-a" }
        assertEquals("Plan", plan.title)
        assertEquals("mynotes:tag:1", plan.tagUid)
        assertEquals(1_700_000_000_000L, plan.dateCreate)
        val trashed = payload.notes.first { it.uid == "note-b" }
        assertTrue(trashed.isTrash)
        assertNull(trashed.tagUid)
        assertTrue(BlockConverter.jsonToBlocks(trashed.value).single() is Block.TextBlock)

        val milk = payload.tasks.first { it.uid == "mynotes:task:1" }
        assertEquals("Buy milk", milk.title)
        assertEquals("and eggs", milk.description)
        assertTrue(milk.isCompleted)
        assertEquals(milk.createdDate, milk.completedDate)
        assertEquals("mynotes:category:1", milk.categoryTagUid)
        val call = payload.tasks.first { it.uid == "mynotes:task:2" }
        assertNull(call.description)
        assertNull(call.completedDate)
    }

    @Test
    fun importsOnceAndARepeatedHandoffAddsNothing() = runTest {
        val store = InMemoryVaultDataStore()
        val first = import(sample, store)
        assertEquals(2, first.notesAdded)
        assertEquals(2, first.tasksAdded)
        assertEquals(3, first.tagsAdded)
        assertEquals(0, first.skipped)

        val second = import(sample, store)
        assertEquals(0, second.notesAdded + second.tasksAdded + second.tagsAdded)
        assertEquals(7, second.skipped)
        assertEquals(2, store.notes.size)
        assertEquals(2, store.tasks.size)
        assertEquals(3, store.tags.size)
    }

    @Test
    fun tagsMatchTheVaultByNameAndCategoriesShareTagsByName() = runTest {
        val store = InMemoryVaultDataStore()
        val work = store.insertTag(Tag(nameTag = "work", position = 0, uid = "encly-work"))
        val json = handoff(
            tags = listOf(tag("t1", " Work "), tag("t2", "Ideas")),
            notes = listOf(note("n1", tag = "Work"), note("n2", tag = "ideas")),
            categories = listOf(category("c1", "IDEAS"), category("c2", "Shopping"), category("c3", " ")),
            tasks = listOf(task("k1", "a", categoryId = "c1"), task("k2", "b", categoryId = "c3")),
        )
        val summary = import(json, store)

        assertEquals(listOf("work", "Ideas", "Shopping"), store.tags.map { it.nameTag })
        assertEquals(2, summary.tagsAdded)
        val ideas = store.tags.first { it.nameTag == "Ideas" }.id
        assertEquals(work, store.notes.first { it.uid == "n1" }.tagId)
        assertEquals(ideas, store.notes.first { it.uid == "n2" }.tagId)
        assertEquals(ideas, store.tasks.first { it.uid == "k1" }.categoryId)
        assertNull(store.tasks.first { it.uid == "k2" }.categoryId)

        // Again: nothing new, and still no second "Work" or "Ideas".
        import(json, store)
        assertEquals(3, store.tags.size)
    }

    @Test
    fun tagMatchByUidOnlyKeepsTheOldBehaviour() = runTest {
        val store = InMemoryVaultDataStore()
        store.insertTag(Tag(nameTag = "Work", uid = "encly-work"))
        BackupImporter.import(mapped(handoff(tags = listOf(tag("t1", "Work")))).payload, ImportMode.MERGE, store)
        assertEquals(2, store.tags.count { it.nameTag == "Work" })
    }

    @Test
    fun longIdsBecomeStableDigests() {
        val longId = "x".repeat(BackupPayloadCodec.MAX_UID_LENGTH + 1)
        val json = handoff(notes = listOf(note(longId)))
        val first = mapped(json).payload.notes.single().uid
        assertEquals(BackupPayloadCodec.MAX_UID_LENGTH, first.length)
        assertEquals(first, mapped(json).payload.notes.single().uid)
        assertEquals("short-id", MyNotesHandoffMapper.uidFor("note", "short-id"))
    }

    @Test
    fun aTagAndACategoryWithTheSameIdGetDifferentUids() {
        val payload = mapped(
            handoff(tags = listOf(tag("same", "A")), categories = listOf(category("same", "B"))),
        ).payload
        assertEquals(2, payload.tags.map { it.uid }.toSet().size)
    }
}
