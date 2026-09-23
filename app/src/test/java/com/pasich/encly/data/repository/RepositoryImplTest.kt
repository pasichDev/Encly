package com.pasich.encly.data.repository

import com.pasich.encly.data.database.AppDatabase
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException

/** Repositories report failures as a Result or a failing flow: never a sentinel 0/false. */
class RepositoryImplTest {
    private val notesDao = mock(NotesDao::class.java)
    private val tagsDao = mock(TagsDao::class.java)
    private val tasksDao = mock(TasksDao::class.java)
    private val database = mock(AppDatabase::class.java).also {
        `when`(it.notesDao()).thenReturn(notesDao)
        `when`(it.tagsDao()).thenReturn(tagsDao)
        `when`(it.tasksDao()).thenReturn(tasksDao)
    }
    private var locked = false
    private val provider = object : DatabaseProvider {
        override fun getDatabase(): AppDatabase {
            if (locked) error("vault locked")
            return database
        }
    }

    @Test
    fun updatingZeroRowsIsAFailure() = runBlocking {
        val note = Note(id = NOTE_ID)
        `when`(notesDao.updateNote(note)).thenReturn(0)

        assertTrue(NotesRepositoryImpl(provider).updateNote(note).isFailure)
    }

    @Test
    fun updatingARowSucceeds() = runBlocking {
        val note = Note(id = NOTE_ID)
        `when`(notesDao.updateNote(note)).thenReturn(1)

        assertTrue(NotesRepositoryImpl(provider).updateNote(note).isSuccess)
    }

    @Test
    fun aStorageExceptionBecomesAFailureNotACrash() = runBlocking {
        val note = Note(id = NOTE_ID)
        `when`(notesDao.insertNote(note)).thenAnswer { throw IOException("disk") }

        assertTrue(NotesRepositoryImpl(provider).insertNote(note).exceptionOrNull() is IOException)
    }

    @Test
    fun aLockedVaultFailsTheWriteInsteadOfThrowing() = runBlocking {
        locked = true

        assertTrue(TasksRepositoryImpl(provider).deleteTaskById(TASK_ID).isFailure)
    }

    @Test
    fun cancellationIsRethrownNotReportedAsAFailure() {
        val note = Note(id = NOTE_ID)
        val cancellation = CancellationException("cancelled")
        runBlocking { `when`(notesDao.updateNote(note)).thenAnswer { throw cancellation } }

        val thrown = runCatching { runBlocking { NotesRepositoryImpl(provider).updateNote(note) } }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun aLockedVaultFailsInsideTheFlowWhereTheScreenHandlesIt() = runBlocking {
        locked = true
        // Requesting the flow must not throw; collecting it fails.
        val flow = NotesRepositoryImpl(provider).getTrashNotes()

        assertTrue(runCatching { flow.first() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun deletingATagAlsoUntagsItsNotesInOneTransaction(): Unit = runBlocking {
        val tag = Tag(id = TAG_ID, nameTag = "Work")
        `when`(tagsDao.deleteTagDetachingNotes(tag)).thenReturn(1)

        assertTrue(TagsRepositoryImpl(provider).deleteTag(tag).isSuccess)

        verify(tagsDao).deleteTagDetachingNotes(tag)
        verify(tagsDao, never()).deleteTag(tag)
    }

    @Test
    fun reorderingFailsUnlessEveryTagIsUpdated() = runBlocking {
        val tags = listOf(Tag(id = 1, nameTag = "a"), Tag(id = 2, nameTag = "b"))
        `when`(tagsDao.updateTags(tags)).thenReturn(1)

        assertTrue(TagsRepositoryImpl(provider).updateTags(tags).isFailure)
        assertTrue(TagsRepositoryImpl(provider).updateTags(emptyList()).isSuccess)
    }

    @Test
    fun tagsComeInTheirDisplayOrder() = runBlocking {
        `when`(tagsDao.getTags()).thenReturn(
            flowOf(listOf(Tag(id = 1, nameTag = "b", position = 2), Tag(id = 2, nameTag = "a", position = 0))),
        )

        assertEquals(listOf(2L, 1L), TagsRepositoryImpl(provider).getTags().first().map { it.id })
    }

    private companion object {
        const val NOTE_ID = 5L
        const val TAG_ID = 8L
        const val TASK_ID = 3L
    }
}
