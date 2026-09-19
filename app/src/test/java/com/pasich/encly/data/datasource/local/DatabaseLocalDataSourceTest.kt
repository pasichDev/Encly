package com.pasich.encly.data.datasource.local

import com.pasich.encly.data.database.AppDatabase
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.dao.NotesDao
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
        val source = sourceFor(notesDao)
        val note = Note(id = NOTE_ID)
        `when`(notesDao.updateNote(note)).thenReturn(0)

        assertFalse(source.updateNote(note))
    }

    @Test
    fun updateNoteReturnsTrueWhenRoomUpdatesARow() = runBlocking {
        val notesDao = mock(NotesDao::class.java)
        val source = sourceFor(notesDao)
        val note = Note(id = NOTE_ID)
        `when`(notesDao.updateNote(note)).thenReturn(1)

        assertTrue(source.updateNote(note))
    }

    private fun sourceFor(notesDao: NotesDao): DatabaseLocalDataSource {
        val database = mock(AppDatabase::class.java)
        `when`(database.notesDao()).thenReturn(notesDao)

        val databaseProvider = object : DatabaseProvider {
            override fun getDatabase(): AppDatabase = database
        }

        return DatabaseLocalDataSource(databaseProvider)
    }

    private companion object {
        const val NOTE_ID = 5L
    }
}
