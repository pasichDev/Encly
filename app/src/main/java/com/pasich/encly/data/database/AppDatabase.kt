package com.pasich.encly.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.utils.DB_VERSION



@Database(
    entities = [Note::class, Tag::class, Task::class], version = DB_VERSION, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
    abstract fun tagsDao(): TagsDao
    abstract fun tasksDao(): TasksDao
}

data class Note(val id: Int = 0, val content: String = "")
data class Tag(val id: Int = 0, val name: String = "")
data class Task(val id: Int = 0, val done: Boolean = false)

