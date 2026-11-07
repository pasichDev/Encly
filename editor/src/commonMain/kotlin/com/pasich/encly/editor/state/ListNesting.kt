package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.isListKind
import com.pasich.encly.editor.model.movedBy
import com.pasich.encly.editor.model.normalizedLevels
import com.pasich.encly.editor.model.shifted
import com.pasich.encly.editor.model.withSiblingsOfKind

// Indenting, outdenting and moving the items of a list. An item moves with the items nested under it, and
// each operation is one undo step. The cursor stays in the item when its field has it.

/** The list [listId] and its items with valid levels, and the position of [itemId] in them. */
private class ListRows(val index: Int, val list: Block.ListBlock, val rows: List<ItemListBlock>, val at: Int)

private fun BlockEditorState.rowsOf(listId: String, itemId: String): ListRows? {
    val where = itemAt(listId, itemId) ?: return null
    val rows = where.list.items.value.normalizedLevels(where.list.blockType)
    return ListRows(where.index, where.list, rows, where.at)
}

/** Whether item [itemId] of list [listId] can go one level [deeper] (or out). */
public fun BlockEditorState.canShiftListItem(listId: String, itemId: String, deeper: Boolean): Boolean =
    rowsOf(listId, itemId)?.let { it.rows.shifted(it.at, deeper, it.list.blockType) } != null

/**
 * Indents ([deeper]) or outdents item [itemId] of list [listId] one level, with the items nested under it.
 * False, and nothing changes, when it cannot (see [shifted]).
 */
public fun BlockEditorState.shiftListItem(listId: String, itemId: String, deeper: Boolean): Boolean {
    val where = rowsOf(listId, itemId)
    val shifted = where?.let { it.rows.shifted(it.at, deeper, it.list.blockType) }
    if (where == null || shifted == null) return false
    commit(where, shifted, itemId)
    return true
}

/** Whether item [itemId] of list [listId] can move [by] places among the items of its own level and parent. */
public fun BlockEditorState.canMoveListItem(listId: String, itemId: String, by: Int): Boolean =
    rowsOf(listId, itemId)?.let { it.rows.movedBy(it.at, by) } != null

/**
 * Moves item [itemId] of list [listId], with the items nested under it, [by] places among the items that share
 * its parent (up is negative). False, and nothing changes, when there is no such place.
 */
public fun BlockEditorState.moveListItem(listId: String, itemId: String, by: Int): Boolean {
    val where = rowsOf(listId, itemId)
    val moved = where?.let { it.rows.movedBy(it.at, by) }
    if (where == null || moved == null) return false
    commit(where, moved.first, itemId)
    return true
}

/**
 * Makes item [itemId] of list [listId] and its siblings (the items of its level under the same parent) a list
 * of [kind]: a checklist, bulleted or numbered. False for an item at the top level, which takes the kind of its
 * whole list (see applyTool), or when it already is [kind].
 */
public fun BlockEditorState.setNestedListKind(listId: String, itemId: String, kind: BlockType): Boolean {
    val where = if (kind.isListKind()) rowsOf(listId, itemId) else null
    val changed = where?.let { it.rows.withSiblingsOfKind(it.at, kind, it.list.blockType) }
    if (where == null || changed == null) return false
    commit(where, changed, itemId)
    return true
}

/** In a nested item of a list, a list tool sets the kind of that sub-list instead of converting the whole list. */
internal fun BlockEditorState.retargetNestedItem(block: Block, tool: BlockType): Boolean {
    val list = block as? Block.ListBlock
    val itemId = list?.let { selection.itemOf(it.id) }
    return list != null && itemId != null && setNestedListKind(list.id, itemId, tool)
}

private fun BlockEditorState.commit(where: ListRows, items: List<ItemListBlock>, itemId: String) {
    val list = where.list
    batch { setValue(list.id, list.items, items) }
    // The item keeps the focus it had, its cursor where it was; one that was not being written in is left alone.
    if (selection.focusedBlockId.value == list.id) {
        selection.focusAt(where.index, caret = selection.caretOf(list.id), itemId = itemId)
    }
}

/** The list and item the toolbar acts on: the item the user last worked in, in the block they work on. */
public fun BlockEditorState.interactedListItem(): Pair<String, String>? {
    val list = blocks.getOrNull(selection.interactedIndex) as? Block.ListBlock
    val itemId = list?.let { l -> selection.itemOf(l.id)?.takeIf { id -> l.items.value.any { it.id == id } } }
    return if (list != null && itemId != null) list.id to itemId else null
}
