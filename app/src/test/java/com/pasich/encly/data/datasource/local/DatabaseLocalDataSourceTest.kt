package com.pasich.encly.data.datasource.local

import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DatabaseLocalDataSourceTest {
    @Test
    fun updateNoteReturnsFalseWhenRoomUpdatesZeroRows() = runBlocking {
        val notesDao = mock(NotesDao::class.java)
        val source = DatabaseLocalDataSource(
            notesDao = notesDao,
            tagsDao = mock(TagsDao::class.java),
            tasksDao = mock(TasksDao::class.java)
        )
        val note = Note(id = NOTE_ID)
        `when`(notesDao.updateNote(note)).thenReturn(0)

        assertFalse(source.updateNote(note))
    }

    @Test
    fun updateNoteReturnsTrueWhenRoomUpdatesARow() = runBlocking {
        val notesDao = mock(NotesDao::class.java)
        val source = DatabaseLocalDataSource(
            notesDao = notesDao,
            tagsDao = mock(TagsDao::class.java),
            tasksDao = mock(TasksDao::class.java)
        )
        val note = Note(id = NOTE_ID)
        `when`(notesDao.updateNote(note)).thenReturn(1)

        assertTrue(source.updateNote(note))
    }

    private companion object {
        const val NOTE_ID = 5L
    }
}
