package com.pasich.encly.data.datasource.local


import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import javax.inject.Inject

class DatabaseLocalDataSource @Inject constructor(
    private val notesDao: NotesDao,
    private val tagsDao: TagsDao,
    private val tasksDao: TasksDao
) {

    // Notes
    suspend fun getNotes() = notesDao.getAllNotes()
    fun getAllNotesWithTag() = notesDao.getAllNotesWithTags()
    fun getNotesByTagId(tagId: Long) = notesDao.getNotesByTagId(tagId)
    suspend fun getNoteById(noteId: Long) = notesDao.getNoteById(noteId)
    suspend fun insertNote(note: Note): Long {
        return try {
            notesDao.insertNote(note)
        } catch (e: Exception) {
            0
        }
    }


    suspend fun updateNote(note: Note): Boolean {
        return try {
            notesDao.updateNote(note)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteNoteById(id: Long): Boolean {
        return try {
            notesDao.deleteNoteById(id)
            true
        } catch (_: Exception) {
            false
        }
    }

    // Tags
    fun getTags() = tagsDao.getTags()
    fun addTags(tags: List<Tag>) = tagsDao.addTags(tags)
    fun addTag(tag: Tag) = tagsDao.addTag(tag)
    fun deleteTag(tag: Tag) = tagsDao.deleteTag(tag)
    fun updateTag(tag: Tag) = tagsDao.updateTag(tag)


    // Trash
    fun getTrashNotes() = notesDao.getTrashNotes()

    // Tasks
    fun getTasksDao() = tasksDao
}
