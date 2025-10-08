package com.pasich.encly.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithTag(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "tagId",
        entityColumn = "id"
    )
    val tag: Tag?
)