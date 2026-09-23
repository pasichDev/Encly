package com.pasich.encly.domain.model

import java.util.UUID

/**
 * One list/checklist row. [id] keys the row's field state in Compose, so deleting or inserting
 * a row cannot hand one row's text field to another. It survives `copy()` and is never
 * serialized (`@Transient`), so stored notes are unchanged.
 */
data class ItemListBlock(
    val value: String = "",
    val isCheck: Boolean = false,
    @Transient val id: String = UUID.randomUUID().toString(),
)
