package com.pasich.encly.data.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where a note shows up, in the real Room schema (plain SQLite under Robolectric, not SQLCipher):
 * the main list, the archive and the trash never overlap, and the new columns default to
 * "not pinned, not archived, no colour".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotesDaoTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.notesDao()

    @Before
    fun setUp() {
        db = VaultSchema.install(
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries(),
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(title: String, tagId: Long? = null, block: Note.() -> Unit = {}): Long =
        dao.insertNote(Note(title = title, tagId = tagId).apply(block))

    private suspend fun titles(flow: Flow<List<NoteWithTag>>) = flow.first().map { it.note.title }.sorted()

    @Test
    fun aNewNoteIsNotPinnedNotArchivedAndHasNoColour() = runBlocking {
        val id = insert("plain")

        val stored = checkNotNull(dao.getNoteById(id))

        assertEquals(false, stored.isPinned)
        assertEquals(false, stored.isArchived)
        assertEquals("none", stored.color)
    }

    @Test
    fun theMainListLeavesOutArchivedAndTrashedNotes() = runBlocking {
        insert("list")
        insert("pinned") { isPinned = true }
        insert("archived") { isArchived = true }
        insert("trashed") { isTrash = true }

        assertEquals(listOf("list", "pinned"), titles(dao.getAllNotesWithTags()))
        assertEquals(listOf("list", "pinned"), dao.getAllNotes().first().map { it.title }.sorted())
    }

    @Test
    fun aTagFilterLeavesOutArchivedNotes() = runBlocking {
        insert("kept", tagId = TAG)
        insert("archived", tagId = TAG) { isArchived = true }
        insert("other", tagId = OTHER_TAG)

        assertEquals(listOf("kept"), titles(dao.getNotesByTagId(TAG)))
    }

    @Test
    fun theArchiveHoldsArchivedNotesThatAreNotInTheTrash() = runBlocking {
        insert("list")
        insert("archived") { isArchived = true }
        insert("archived and trashed") {
            isArchived = true
            isTrash = true
        }

        assertEquals(listOf("archived"), titles(dao.getArchivedNotesWithTags()))
    }

    @Test
    fun aTrashedArchivedNoteIsInTheTrashAndComesBackToTheArchive() = runBlocking {
        val id = insert("both") { isArchived = true }

        dao.updateNote(checkNotNull(dao.getNoteById(id)).copy(isTrash = true))
        assertEquals(listOf("both"), dao.getTrashNotes().first().map { it.title })
        assertTrue(titles(dao.getArchivedNotesWithTags()).isEmpty())

        dao.updateNote(checkNotNull(dao.getNoteById(id)).copy(isTrash = false))
        assertTrue(dao.getTrashNotes().first().isEmpty())
        assertEquals(listOf("both"), titles(dao.getArchivedNotesWithTags()))
        assertTrue(titles(dao.getAllNotesWithTags()).isEmpty())
    }

    @Test
    fun theBackupSnapshotKeepsPinArchiveAndColour() = runBlocking {
        insert("styled") {
            isPinned = true
            color = "teal"
        }

        val stored = db.backupDao().allNotes().single()

        assertEquals(true, stored.isPinned)
        assertEquals("teal", stored.color)
    }

    @Test
    fun aColourWrittenAfterATagChangeKeepsTheNewTag() = runBlocking {
        db.tagsDao().addTags(listOf(Tag(id = TAG, nameTag = "X"), Tag(id = OTHER_TAG, nameTag = "Y")))
        val id = insert("note", tagId = TAG)
        // What a stale sheet holds: the note as read in tag X's view.
        val stale = checkNotNull(dao.getNoteById(id))

        dao.updateTagId(id, OTHER_TAG)
        dao.updateColor(stale.id, "teal")

        val stored = checkNotNull(dao.getNoteById(id))
        assertEquals(OTHER_TAG, stored.tagId)
        assertEquals("teal", stored.color)
    }

    @Test
    fun everySingleColumnWriteLeavesTheOtherColumnsAlone() = runBlocking {
        val id = insert("note", tagId = TAG) {
            description = "d"
            color = "red"
            isPinned = true
        }
        val before = checkNotNull(dao.getNoteById(id))

        dao.updateDescription(id, "new")
        dao.updateColor(id, "blue")
        assertEquals(before.copy(description = "new", color = "blue"), dao.getNoteById(id))
        dao.updateTagId(id, null)
        assertEquals(before.copy(description = "new", color = "blue", tagId = null), dao.getNoteById(id))
    }

    @Test
    fun archivingUnpinsInTheSameWriteAndUnarchivingKeepsThePinState() = runBlocking {
        val id = insert("note") { isPinned = true }

        dao.updateArchived(id, true)
        val archived = checkNotNull(dao.getNoteById(id))
        assertEquals(true, archived.isArchived)
        assertEquals(false, archived.isPinned)

        dao.updateArchived(id, false)
        assertEquals(false, checkNotNull(dao.getNoteById(id)).isPinned)
    }

    @Test
    fun anArchivedNoteCannotBePinnedButCanBeUnpinned() = runBlocking {
        val id = insert("note") { isArchived = true }

        dao.updatePinned(id, true)
        assertEquals(false, checkNotNull(dao.getNoteById(id)).isPinned)

        dao.updateArchived(id, false)
        dao.updatePinned(id, true)
        assertEquals(true, checkNotNull(dao.getNoteById(id)).isPinned)
        dao.updatePinned(id, false)
        assertEquals(false, checkNotNull(dao.getNoteById(id)).isPinned)
    }

    @Test
    fun aTagsArchivedNotesAreReadByTag() = runBlocking {
        insert("live", tagId = TAG)
        insert("old", tagId = TAG) { isArchived = true }
        insert("trashed", tagId = TAG) {
            isArchived = true
            isTrash = true
        }
        insert("other", tagId = OTHER_TAG) { isArchived = true }

        assertEquals(listOf("old"), titles(dao.getArchivedNotesByTagId(TAG)))
    }

    @Test
    fun aNoteCanBeObservedByIdWithItsTag() = runBlocking {
        db.tagsDao().addTags(listOf(Tag(id = TAG, nameTag = "X")))
        val id = insert("note", tagId = TAG) { isArchived = true }

        val live = checkNotNull(dao.observeNoteWithTag(id).first())

        assertEquals("X", live.tag?.nameTag)
        assertEquals(null, dao.observeNoteWithTag(999).first())
    }

    private companion object {
        const val TAG = 1L
        const val OTHER_TAG = 2L
    }
}
