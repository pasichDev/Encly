package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/**
 * Whole-vault reads and writes for encrypted export/import. Inserts ABORT on a uid clash so a
 * bad import fails (and its transaction rolls back) instead of silently replacing a row.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM notes ORDER BY id")
    suspend fun allNotes(): List<Note>

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun allTags(): List<Tag>

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun allTasks(): List<Task>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: Task): Long

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
