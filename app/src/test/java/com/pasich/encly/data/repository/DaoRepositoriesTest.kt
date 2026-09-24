package com.pasich.encly.data.repository

import com.pasich.encly.data.database.AppDatabase
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The repositories pass reads and writes to the DAO of the database open at call time. */
class DaoRepositoriesTest {
    private val notesDao = mock(NotesDao::class.java)
    private val tagsDao = mock(TagsDao::class.java)
    private val tasksDao = mock(TasksDao::class.java)
    private var database = databaseWith(notesDao, tagsDao, tasksDao)
    private val provider = object : DatabaseProvider {
        override fun getDatabase(): AppDatabase = database
    }

    private val tasks = TasksRepositoryImpl(provider)
    private val notes = NotesRepositoryImpl(provider)
    private val tags = TagsRepositoryImpl(provider)

    @Test
    fun taskFlowsComeFromTheDao() = runBlocking {
        val active = listOf(Task(id = 1, title = "a"))
        val done = listOf(Task(id = 2, title = "b", isCompleted = true))
        `when`(tasksDao.getAllActiveTasks()).thenReturn(flowOf(active))
        `when`(tasksDao.getAllCompletedTasks()).thenReturn(flowOf(done))
        `when`(tasksDao.getAllTasks()).thenReturn(flowOf(active + done))
        `when`(tasksDao.getActiveTasksCount()).thenReturn(flowOf(1))
        `when`(tasksDao.getCompletedTasksCount()).thenReturn(flowOf(1))

        assertEquals(active, tasks.getAllActiveTasks().first())
        assertEquals(done, tasks.getAllCompletedTasks().first())
        assertEquals(active + done, tasks.getAllTasks().first())
        assertEquals(1, tasks.getActiveTasksCount().first())
        assertEquals(1, tasks.getCompletedTasksCount().first())
    }

    @Test
    fun taskWritesReportTheRowsTheyChanged() = runBlocking {
        val task = Task(id = 3, title = "t")
        `when`(tasksDao.insertTask(task)).thenReturn(3L)
        `when`(tasksDao.updateTask(task)).thenReturn(1)
        `when`(tasksDao.updateTaskStatus(3, true, 9L)).thenReturn(1)
        `when`(tasksDao.updateTaskStatus(4, true, 9L)).thenReturn(0)
        `when`(tasksDao.deleteTaskById(3)).thenReturn(1)
        `when`(tasksDao.deleteAllCompletedTasks()).thenReturn(0)

        assertEquals(3L, tasks.insertTask(task).getOrNull())
        assertTrue(tasks.updateTask(task).isSuccess)
        assertTrue(tasks.updateTaskStatus(3, true, 9L).isSuccess)
        assertTrue("a task that is gone is a failure", tasks.updateTaskStatus(4, true, 9L).isFailure)
        assertTrue(tasks.deleteTaskById(3).isSuccess)
        assertTrue("clearing nothing is fine", tasks.deleteAllCompletedTasks().isSuccess)
    }

    @Test
    fun noteReadsAndDeletesGoThroughTheDao() = runBlocking {
        val note = Note(id = 5, title = "n")
        val withTag = listOf(NoteWithTag(note, Tag(id = 1)))
        `when`(notesDao.getAllNotesWithTags()).thenReturn(flowOf(withTag))
        `when`(notesDao.getNotesByTagId(1)).thenReturn(flowOf(withTag))
        `when`(notesDao.getNoteById(5)).thenReturn(note)
        `when`(notesDao.deleteNoteById(5)).thenReturn(1)
        `when`(notesDao.deleteNoteById(6)).thenReturn(0)

        assertEquals(withTag, notes.getAllNotesWithTag().first())
        assertEquals(withTag, notes.getNotesByTagId(1).first())
        assertEquals(note, notes.getNoteById(5))
        assertNull(notes.getNoteById(6))
        assertTrue(notes.deleteNoteById(5).isSuccess)
        assertTrue(notes.deleteNoteById(6).isFailure)
    }

    @Test
    fun tagWritesReportTheRowsTheyChanged(): Unit = runBlocking {
        val tag = Tag(id = 2, nameTag = "Work")
        `when`(tagsDao.addTag(tag)).thenReturn(2L)
        `when`(tagsDao.updateTag(tag)).thenReturn(1)

        assertEquals(2L, tags.addTag(tag).getOrNull())
        assertTrue(tags.updateTag(tag).isSuccess)
        verify(tagsDao).updateTag(tag)
    }

    @Test
    fun eachCallUsesTheDatabaseOpenAtThatMoment(): Unit = runBlocking {
        val reopened = mock(TasksDao::class.java)
        `when`(reopened.deleteTaskById(1)).thenReturn(1)
        database = databaseWith(notesDao, tagsDao, reopened)

        assertTrue(tasks.deleteTaskById(1).isSuccess)
        verify(reopened).deleteTaskById(1)
    }

    private fun databaseWith(notes: NotesDao, tags: TagsDao, tasks: TasksDao) = mock(AppDatabase::class.java).also {
        `when`(it.notesDao()).thenReturn(notes)
        `when`(it.tagsDao()).thenReturn(tags)
        `when`(it.tasksDao()).thenReturn(tasks)
    }
}
