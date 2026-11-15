package com.pasich.encly.data.database

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The DAO transactions and the entity helpers, against a real encrypted vault on the desktop. */
class DesktopDaoTest {
    private lateinit var directory: java.io.File
    private lateinit var manager: SecureDatabaseManager
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("encly-dao-test").toFile()
        manager = SecureDatabaseManager(DesktopDatabasePlatform(Path(directory.path)))
        assertTrue(manager.unlockDatabase(KEY, allowCreate = true))
        db = manager.getDatabase(checkNotNull(manager.captureLease()))
    }

    @AfterTest
    fun tearDown() {
        manager.reset()
        directory.deleteRecursively()
    }

    @Test
    fun deletingATagUntagsItsNotesAndLeavesOthersAlone() = runBlocking {
        val work = db.tagsDao().addTag(Tag(nameTag = "Work"))
        val home = db.tagsDao().addTag(Tag(nameTag = "Home"))
        val inWork = db.notesDao().insertNoteWithTags(Note(title = "w", tagIds = setOf(work)))
        val inBoth = db.notesDao().insertNoteWithTags(Note(title = "b", tagIds = setOf(work, home)))
        val inHome = db.notesDao().insertNoteWithTags(Note(title = "h", tagIds = setOf(home)))

        db.tagsDao().deleteTagDetachingNotes(Tag(id = work, nameTag = "Work"))

        assertEquals(emptySet<Long>(), tagIdsOf(inWork))
        assertEquals(setOf(home), tagIdsOf(inBoth))
        assertEquals(setOf(home), tagIdsOf(inHome))
    }

    @Test
    fun theDatabaseCascadesTheLinksOfADeletedTagOrNote() = runBlocking {
        val work = db.tagsDao().addTag(Tag(nameTag = "Work"))
        val home = db.tagsDao().addTag(Tag(nameTag = "Home"))
        val note = db.notesDao().insertNoteWithTags(Note(title = "n", tagIds = setOf(work, home)))
        val other = db.notesDao().insertNoteWithTags(Note(title = "o", tagIds = setOf(home)))

        // The plain delete, without the explicit unlinking: the foreign key alone must remove the link.
        db.tagsDao().deleteTag(Tag(id = work, nameTag = "Work"))
        assertEquals(setOf(home), tagIdsOf(note))

        db.notesDao().deleteNoteById(note)
        assertEquals(listOf(other), db.backupDao().allNoteTags().map { it.noteId })
    }

    @Test
    fun aLinkToATagThatDoesNotExistIsSkippedAndTheRestIsKept() = runBlocking {
        val real = db.tagsDao().addTag(Tag(nameTag = "Real"))
        val note = db.notesDao().insertNote(Note(title = "n"))

        // A tag deleted while an editor still held its id must not fail the save: the dead id is skipped.
        db.notesDao().changeNoteTags(note, added = listOf(404L, real), removed = emptyList())
        assertEquals(setOf(real), tagIdsOf(note))

        db.notesDao().setNoteTags(note, listOf(404L, real))
        assertEquals(setOf(real), tagIdsOf(note))

        val created = db.notesDao().insertNoteWithTags(Note(title = "c", tagIds = setOf(404L, real)))
        assertEquals(setOf(real), tagIdsOf(created))
    }

    @Test
    fun emptyingTheTrashDeletesOnlyTrashedNotes() = runBlocking {
        val kept = db.notesDao().insertNote(Note(title = "kept"))
        val a = db.notesDao().insertNote(Note(title = "a", isTrash = true, trashedAt = 1L))
        val b = db.notesDao().insertNote(Note(title = "b", isTrash = true, trashedAt = 2L))
        val c = db.notesDao().insertNote(Note(title = "c", isTrash = true, trashedAt = 3L))

        // A selected note that was restored meanwhile is not trashed and stays.
        assertEquals(2, db.notesDao().deleteTrashedNotes(listOf(a, b, kept)))
        assertEquals(listOf(c), db.notesDao().getTrashNotes().first().map { it.id })

        assertEquals(1, db.notesDao().deleteAllTrashed())
        assertEquals(emptyList<Long>(), db.notesDao().getTrashNotes().first().map { it.id })
        assertEquals(1, db.notesDao().getAllNotes().first().size)
    }

    private suspend fun tagIdsOf(noteId: Long) =
        checkNotNull(db.notesDao().getNoteWithTags(noteId)).tags.map { it.id }.toSet()

    @Test
    fun subtasksAreAppendedRestoredAndReplacedInOrder() = runBlocking {
        val taskId = db.tasksDao().insertTask(Task(title = "task"))
        db.tasksDao().appendSubtask(taskId, "a")
        db.tasksDao().appendSubtask(taskId, "b")
        assertEquals(listOf("a" to 0, "b" to 1), titlesAndPositions(taskId))

        db.tasksDao().restoreSubtask(Subtask(taskId = taskId, title = "back", position = 0))
        assertEquals(listOf("back" to 0, "a" to 1, "b" to 2), titlesAndPositions(taskId))

        val keep = db.tasksDao().getSubtasks(taskId).first { it.title == "a" }
        db.tasksDao().replaceSubtasks(taskId, listOf(Subtask(taskId = taskId, title = "new"), keep.copy(title = "a2")))
        assertEquals(listOf("new" to 0, "a2" to 1), titlesAndPositions(taskId))
        assertEquals(keep.uid, db.tasksDao().getSubtasks(taskId).first { it.title == "a2" }.uid)
    }

    @Test
    fun aDeletedTaskComesBackWithItsSubtasks() = runBlocking {
        val task = Task(id = 7, title = "gone")

        db.tasksDao().restoreTask(
            task,
            listOf(Subtask(taskId = 0, title = "s1"), Subtask(taskId = 0, title = "s2", position = 1)),
        )

        assertEquals("gone", checkNotNull(db.tasksDao().getTaskById(7)).title)
        assertEquals(listOf("s1" to 0, "s2" to 1), titlesAndPositions(7))
    }

    @Test
    fun theHelpersOfNotesAndTasksFillTheirDefaults() {
        val note = Note.new("title", "[]", tagIds = setOf(3))
        assertTrue(note.date > 0 && note.dateCreate > 0)
        assertEquals(setOf(3L), note.tagIds)
        assertEquals(Note.NO_COLOR, note.color)

        val task = Task.new("t", description = "d", priority = 2, categoryId = 5)
        assertTrue(task.createdDate > 0)
        assertEquals(2, task.priority)
        assertEquals(5L, task.categoryId)

        assertTrue(Note().isEmpty())
        assertFalse(Note(title = "x").isEmpty())
        assertTrue(note.hasContentChanged("[]", "other title"))
        assertTrue(note.hasContentChanged("[1]", "title"))
        assertFalse(note.hasContentChanged("[]", "title"))
    }

    private suspend fun titlesAndPositions(taskId: Long) = db.tasksDao().getSubtasks(taskId).map {
        it.title to
            it.position
    }

    private companion object {
        val KEY = ByteArray(32) { (it + 3).toByte() }
    }
}
