package com.pasich.encly.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pasich.encly.data.database.dao.BackupDao
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/** Room schema version. Every bump needs a migration and an exported schema in app/schemas. */
const val DB_VERSION = 3

@Database(
    entities = [Note::class, Tag::class, Task::class],
    version = DB_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
    abstract fun tagsDao(): TagsDao
    abstract fun tasksDao(): TasksDao
    abstract fun backupDao(): BackupDao
}
