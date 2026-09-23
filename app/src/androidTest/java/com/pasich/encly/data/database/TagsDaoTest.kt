package com.pasich.encly.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Deleting a tag must not leave notes pointing at it. Plain (non-SQLCipher) in-memory DB. */
@RunWith(AndroidJUnit4::class)
class TagsDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = VaultSchema.install(
            Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java,
            ),
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingATagUntagsItsNotesAndLeavesOthersAlone() = runBlocking {
        val work = db.tagsDao().addTag(Tag(nameTag = "Work"))
        val home = db.tagsDao().addTag(Tag(nameTag = "Home"))
        val workNote = db.notesDao().insertNote(Note(title = "w", tagId = work))
        val homeNote = db.notesDao().insertNote(Note(title = "h", tagId = home))

        assertEquals(1, db.tagsDao().deleteTagDetachingNotes(Tag(id = work, nameTag = "Work")))

        assertNull(db.notesDao().getNoteById(workNote)?.tagId)
        assertEquals(home, db.notesDao().getNoteById(homeNote)?.tagId)
    }
}
