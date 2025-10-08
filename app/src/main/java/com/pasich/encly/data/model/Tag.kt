package com.pasich.encly.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    @ColumnInfo(name = "name")
    var nameTag: String = "",
    @ColumnInfo(name = "visibility")
    var isVisible: Boolean = true,
    @ColumnInfo(name = "position")
    var position: Int = 0
) {
    fun create(nameTag: String, visibility: Boolean = true, position: Int = 0): Tag {
        this.nameTag = nameTag
        this.isVisible = visibility
        this.position = position
        return this
    }
}