package com.pasich.encly.data.database

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        val inWork = db.notesDao().insertNote(Note(title = "w", tagId = work))
        val inHome = db.notesDao().insertNote(Note(title = "h", tagId = home))

        db.tagsDao().deleteTagDetachingNotes(Tag(id = work, nameTag = "Work"))

        assertNull(checkNotNull(db.notesDao().getNoteById(inWork)).tagId)
        assertEquals(home, checkNotNull(db.notesDao().getNoteById(inHome)).tagId)
    }

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
        val note = Note.new("title", "[]", tagId = 3)
        assertTrue(note.date > 0 && note.dateCreate > 0)
        assertEquals(3L, note.tagId)
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
