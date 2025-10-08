package com.pasich.encly.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val createdDate: Long = System.currentTimeMillis(),
    val completedDate: Long? = null,
    val reminderDate: Long? = null,
    val priority: Int = 0, // 0 - Low, 1 - Medium, 2 - High
    val categoryId: Long? = null,
    var position: Int = 0
) {
    companion object {
        fun new(
            title: String,
            description: String? = null,
            reminderDate: Long? = null,
            priority: Int = 0,
            categoryId: Long? = null
        ): Task {
            return Task(
                title = title,
                description = description,
                reminderDate = reminderDate,
                priority = priority,
                categoryId = categoryId
            )
        }
    }
}
