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
import org.junit.Assert.assertNotNull
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

        dao.updateTrashed(id, true, TRASHED_AT)
        assertEquals(listOf("both"), dao.getTrashNotes().first().map { it.title })
        assertTrue(titles(dao.getArchivedNotesWithTags()).isEmpty())

        dao.updateTrashed(id, false, null)
        assertTrue(dao.getTrashNotes().first().isEmpty())
        assertEquals(listOf("both"), titles(dao.getArchivedNotesWithTags()))
        assertTrue(titles(dao.getAllNotesWithTags()).isEmpty())
    }

    @Test
    fun anEditorSaveKeepsWhatAnotherWindowDidToTheRow() = runBlocking {
        val id = insert("note") { value = "old" }
        // Another window moves the note to the trash and then archives and pins it...
        dao.updateTrashed(id, true, TRASHED_AT)

        // ...and the editor, which opened the note before, saves its text and the colour it changed.
        dao.updateEdited(
            id, "edited", "new", false, "", 0L, false, null, false, false, true, "teal", false, null, false, null,
        )

        val stored = checkNotNull(dao.getNoteById(id))
        assertEquals("edited", stored.title)
        assertEquals("new", stored.value)
        assertEquals("teal", stored.color)
        assertEquals(true, stored.isTrash)
        assertEquals(TRASHED_AT, stored.trashedAt)
    }

    @Test
    fun anEditorSaveDoesNotUnarchiveOrUnpinWhatItDidNotChange() = runBlocking {
        val id = insert("note") {
            isArchived = true
            isPinned = false
        }

        dao.updateEdited(
            id, "note", "text", false, "", 0L, false, null, false, true, false, "none", false, null, false, null,
        )

        // The editor never wrote the pin (its flag is off), and the archive is not its column at all.
        val stored = checkNotNull(dao.getNoteById(id))
        assertEquals(true, stored.isArchived)
        assertEquals(false, stored.isPinned)
    }

    @Test
    fun anEditorPinNeverLandsOnAnArchivedNote() = runBlocking {
        val id = insert("note") { isArchived = true }

        dao.updateEdited(
            id, "note", "", false, "", 0L, false, null, true, true, false, "none", false, null, false, null,
        )

        assertEquals(false, checkNotNull(dao.getNoteById(id)).isPinned)
    }

    @Test
    fun theDateAndSummaryChangeOnlyWithTheContent() = runBlocking {
        val id = insert("note") {
            date = 5L
            description = "summary"
        }

        dao.updateEdited(
            id, "note", "same", false, "", 99L, false, null, false, false, false, "none", false, null, false, null,
        )
        assertEquals(5L, checkNotNull(dao.getNoteById(id)).date)
        assertEquals("summary", checkNotNull(dao.getNoteById(id)).description)

        dao.updateEdited(
            id, "note", "changed", true, "", 99L, false, null, false, false, false, "none", false, null, false, null,
        )
        assertEquals(99L, checkNotNull(dao.getNoteById(id)).date)
        assertEquals("", checkNotNull(dao.getNoteById(id)).description)
    }

    @Test
    fun aPartialUpdateKeepsTheUid() = runBlocking {
        val id = insert("note")
        val uid = checkNotNull(dao.getNoteById(id)).uid
        assertTrue(uid.isNotEmpty())

        dao.updateEdited(
            id, "again", "x", false, "", 0L, false, null, false, false, false, "none", false, null, false, null,
        )

        assertEquals(uid, checkNotNull(dao.getNoteById(id)).uid)
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
    fun aTagsNotesAreReadWithItsArchivedOnesInOneQuery() = runBlocking {
        insert("live", tagId = TAG)
        insert("old", tagId = TAG) { isArchived = true }
        insert("trashed", tagId = TAG) {
            isArchived = true
            isTrash = true
        }
        insert("other", tagId = OTHER_TAG) { isArchived = true }

        assertEquals(listOf("live", "old"), titles(dao.getNotesOfTagWithArchived(TAG)))
    }

    @Test
    fun aNoteCanBeObservedByIdWithItsTag() = runBlocking {
        db.tagsDao().addTags(listOf(Tag(id = TAG, nameTag = "X")))
        val id = insert("note", tagId = TAG) { isArchived = true }

        val live = checkNotNull(dao.observeNoteWithTag(id).first())

        assertEquals("X", live.tag?.nameTag)
        assertEquals(null, dao.observeNoteWithTag(999).first())
    }

    @Test
    fun stampingGivesATrashTimeOnlyToTrashedNotesWithoutOne() = runBlocking {
        val timed = insert("timed") {
            isTrash = true
            trashedAt = EARLIER
        }
        val untimed = insert("untimed") { isTrash = true }
        val live = insert("live")

        assertEquals(1, dao.stampTrashedAt(STAMP))

        assertEquals(EARLIER, dao.getNoteById(timed)?.trashedAt)
        assertEquals(STAMP, dao.getNoteById(untimed)?.trashedAt)
        assertEquals(null, dao.getNoteById(live)?.trashedAt)
    }

    @Test
    fun deletingExpiredTrashTouchesOnlyTrashedNotesOlderThanTheCutoff() = runBlocking {
        val old = insert("old") {
            isTrash = true
            trashedAt = EARLIER
        }
        val restored = insert("restored") { trashedAt = EARLIER }
        val recent = insert("recent") {
            isTrash = true
            trashedAt = STAMP + 1
        }
        val untimed = insert("untimed") { isTrash = true }
        val future = insert("future") {
            isTrash = true
            trashedAt = Long.MAX_VALUE
        }

        assertEquals(1, dao.deleteTrashedBefore(STAMP))

        assertEquals(null, dao.getNoteById(old))
        listOf(restored, recent, untimed, future).forEach { assertNotNull(dao.getNoteById(it)) }
    }

    private companion object {
        const val EARLIER = 1_000L
        const val TRASHED_AT = 3_000L
        const val STAMP = 2_000L
        const val TAG = 1L
        const val OTHER_TAG = 2L
    }
}
