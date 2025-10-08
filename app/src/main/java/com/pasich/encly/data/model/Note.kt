package com.pasich.encly.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var title: String = "",
    var value: String = "",
    var description: String = "",
    var date: Long = 0L,
    var dateCreate: Long = 0L,
    var tagId: Long? = null,
    @Ignore var isChecked: Boolean = false,
    var isTrash: Boolean = false,
) {


    /**
     * Возвращает true, если заметка не содержит ни заголовка, ни содержимого
     */
    fun isEmpty(): Boolean = title.isBlank() && value.isBlank()


    /**
     * Проверяет, есть ли изменения в содержимом или заголовке заметки
     */
    internal fun hasContentChanged(newBlocksJson: String, newTitle: String): Boolean {
        if (title != newTitle) {
            return true
        }
        // Проверяем изменения в контенте блоков
        return value != newBlocksJson
    }

    companion object {
        fun new(title: String, value: String, tagId: Long? = null): Note {
            return Note(
                title = title,
                value = value,
                date = System.currentTimeMillis(),
                dateCreate = System.currentTimeMillis(),
                tagId = tagId
            )
        }
    }
}
