package com.pasich.encly.editor.model

import kotlin.uuid.Uuid

/**
 * One list/checklist row. [id] keys the row's field state in Compose, so deleting or inserting
 * a row cannot hand one row's text field to another. It survives `copy()` and is never
 * serialized (the codec writes [value] and [isCheck], and [level] and [kind] only when set), so
 * stored notes without sub-lists are unchanged.
 *
 * [level] is how deeply the row is nested under the rows above it (0 is the top, at most
 * [MAX_LIST_LEVEL]). A nested row may be of another [kind] than its list (a checklist under a
 * bullet); null means the list's own kind, and a row at level 0 is always of the list's kind.
 * See `ListLevels.kt` for the rules that keep the levels valid.
 */
public data class ItemListBlock(
    val value: String = "",
    val isCheck: Boolean = false,
    val id: String = Uuid.random().toString(),
    val level: Int = 0,
    val kind: BlockType? = null,
)
