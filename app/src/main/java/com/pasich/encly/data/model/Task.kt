package com.pasich.encly.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** [uid]: stable backup identity, see [Note.uid]. */
@Entity(tableName = "tasks", indices = [Index(value = ["uid"], unique = true)])
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val createdDate: Long = System.currentTimeMillis(),
    val completedDate: Long? = null,
    val priority: Int = 0, // 0 - Low, 1 - Medium, 2 - High
    val categoryId: Long? = null,
    var position: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val uid: String = "",
) {
    companion object {
        fun new(title: String, description: String? = null, priority: Int = 0, categoryId: Long? = null): Task = Task(
            title = title,
            description = description,
            priority = priority,
            categoryId = categoryId,
        )
    }
}
