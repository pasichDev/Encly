package com.pasich.encly.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** [uid]: stable backup identity, see [Note.uid]. */
@Entity(tableName = "tags", indices = [Index(value = ["uid"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    @ColumnInfo(name = "name")
    var nameTag: String = "",
    @ColumnInfo(name = "visibility")
    var isVisible: Boolean = true,
    @ColumnInfo(name = "position")
    var position: Int = 0,
    @ColumnInfo(defaultValue = "''")
    var uid: String = "",
)
