package com.pasich.encly.data.datasource.local

import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import javax.inject.Inject

private const val TAG = "DatabaseLocalDataSource"

/**
 * Database facade that resolves DAOs from the currently unlocked Room instance.
 *
 * The encrypted database is deliberately closed on every app background. Caching an
 * AppDatabase or DAO in Hilt would therefore leave repositories pointing at a closed
 * Room instance after the first re-lock/unlock cycle.
 */
class DatabaseLocalDataSource @Inject constructor(
    private val secureDatabaseManager: SecureDatabaseManager
) {
    private fun notesDao() = secureDatabaseManager.getDatabase().notesDao()
    private fun tagsDao() = secureDatabaseManager.getDatabase().tagsDao()
    private fun tasksDao() = secureDatabaseManager.getDatabase().tasksDao()

    // Notes
    suspend fun getNotes() = notesDao().getAllNotes()
    fun getAllNotesWithTag() = notesDao().getAllNotesWithTags()
    fun getNotesByTagId(tagId: Long) = notesDao().getNotesByTagId(tagId)
    suspend fun getNoteById(noteId: Long) = notesDao().getNoteById(noteId)

    suspend fun insertNote(note: Note): Long =
        try {
            notesDao().insertNote(note)
        } catch (e: Exception) {
            AppLogger.e(TAG, "insertNote failed", e)
            0
        }

    suspend fun updateNote(note: Note): Boolean =
        try {
            notesDao().updateNote(note) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateNote failed", e)
            false
        }

    suspend fun deleteNoteById(id: Long): Boolean =
        try {
            notesDao().deleteNoteById(id) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteNoteById failed", e)
            false
        }

    // Tags
    fun getTags() = tagsDao().getTags()

    suspend fun addTags(tags: List<Tag>): Boolean = runTagWrite("addTags", false) {
        tagsDao().addTags(tags)
        true
    }

    suspend fun addTag(tag: Tag): Long = runTagWrite("addTag", 0L) {
        tagsDao().addTag(tag)
    }

    suspend fun deleteTag(tag: Tag): Boolean = runTagWrite("deleteTag", false) {
        tagsDao().deleteTag(tag) > 0
    }

    suspend fun updateTag(tag: Tag): Boolean = runTagWrite("updateTag", false) {
        tagsDao().updateTag(tag) > 0
    }

    suspend fun updateTags(tags: List<Tag>): Boolean {
        if (tags.isEmpty()) return true

        return runTagWrite("updateTags", false) {
            tagsDao().updateTags(tags) == tags.size
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> runTagWrite(
        operation: String,
        failureValue: T,
        block: suspend () -> T
    ): T = try {
        block()
    } catch (exception: Exception) {
        // Storage failures can originate in Room, SQLCipher, or a closed vault. They must
        // become a safe operation failure instead of leaking a backend exception to the UI.
        AppLogger.e(TAG, "$operation failed", exception)
        failureValue
    }

    // Trash
    fun getTrashNotes() = notesDao().getTrashNotes()

    // Tasks
    fun getAllActiveTasks() = tasksDao().getAllActiveTasks()
    fun getAllCompletedTasks() = tasksDao().getAllCompletedTasks()
    fun getAllTasks() = tasksDao().getAllTasks()
    fun getActiveTasksCount() = tasksDao().getActiveTasksCount()
    fun getCompletedTasksCount() = tasksDao().getCompletedTasksCount()
    suspend fun getTaskById(id: Long) = tasksDao().getTaskById(id)
    suspend fun getTasksWithReminder(currentTime: Long) = tasksDao().getTasksWithReminder(currentTime)
    suspend fun insertTask(task: com.pasich.encly.data.model.Task): Long =
        try {
            tasksDao().insertTask(task)
        } catch (e: Exception) {
            AppLogger.e(TAG, "insertTask failed", e)
            0L
        }
    suspend fun updateTask(task: com.pasich.encly.data.model.Task): Boolean =
        try {
            tasksDao().updateTask(task) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateTask failed", e)
            false
        }
    suspend fun updateTaskStatus(
        id: Long,
        isCompleted: Boolean,
        completedDate: Long?
    ): Boolean = try {
        tasksDao().updateTaskStatus(id, isCompleted, completedDate) > 0
    } catch (e: Exception) {
        AppLogger.e(TAG, "updateTaskStatus failed", e)
        false
    }
    suspend fun deleteTask(task: com.pasich.encly.data.model.Task): Boolean =
        try {
            tasksDao().deleteTask(task) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteTask failed", e)
            false
        }
    suspend fun deleteAllCompletedTasks(): Boolean =
        try {
            tasksDao().deleteAllCompletedTasks()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteAllCompletedTasks failed", e)
            false
        }
    suspend fun deleteTaskById(id: Long): Boolean =
        try {
            tasksDao().deleteTaskById(id) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteTaskById failed", e)
            false
        }
}
