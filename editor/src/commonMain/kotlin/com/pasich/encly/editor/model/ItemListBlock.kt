package com.pasich.encly.editor.model

import kotlin.uuid.Uuid

/**
 * One list/checklist row. [id] keys the row's field state in Compose, so deleting or inserting
 * a row cannot hand one row's text field to another. It survives `copy()` and is never
 * serialized (the codec writes only [value] and [isCheck]), so stored notes are unchanged.
 */
public data class ItemListBlock(
    val value: String = "",
    val isCheck: Boolean = false,
    val id: String = Uuid.random().toString(),
)
