package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.TextualBlock

/** Where the cursor goes after an undo or a redo: block [blockId], in a list its item [itemId], at [caret]. */
internal data class CaretTarget(val blockId: String, val itemId: String?, val caret: Int)

/**
 * The cursor position a history step implies, worked out from the step's own change ([before] and
 * [after] are the field's values around the undo or redo just made; [after] is what the field shows
 * now), never from where the cursor happened to be last. Null for a field it knows nothing about, or
 * a change with no text in it (a ticked box): the cursor stays where it is.
 */
internal fun caretTargetOf(blockId: String, before: Any?, after: Any?): CaretTarget? = when {
    before is String && after is String -> changedCaret(before, after)?.let { CaretTarget(blockId, null, it) }

    before is List<*> && after is List<*> ->
        listCaret(blockId, before.filterIsInstance<ItemListBlock>(), after.filterIsInstance<ItemListBlock>())

    else -> null
}

/**
 * The end of the part of [after] that differs from [before]: where typing began after an undo of
 * typing, after the restored text after an undo of a deletion, after the typed text after a redo.
 * Null when the texts are equal.
 */
internal fun changedCaret(before: String, after: String): Int? {
    if (before == after) return null
    val limit = minOf(before.length, after.length)
    var prefix = 0
    while (prefix < limit && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < limit - prefix && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
    return after.length - suffix
}

private fun listCaret(blockId: String, before: List<ItemListBlock>, after: List<ItemListBlock>): CaretTarget? {
    val beforeIds = before.mapTo(HashSet()) { it.id }
    val afterIds = after.mapTo(HashSet()) { it.id }
    return when {
        // An item came back or appeared: the cursor starts it.
        after.size > before.size -> after.firstOrNull { it.id !in beforeIds }?.let { CaretTarget(blockId, it.id, 0) }

        after.size < before.size -> mergedOrRemoved(blockId, before, after, afterIds)

        else -> after.indices.firstNotNullOfOrNull { i ->
            before.firstOrNull { it.id == after[i].id }
                ?.let { changedCaret(it.value, after[i].value) }
                ?.let { CaretTarget(blockId, after[i].id, it) }
        }
    }
}

/** Items went away: the cursor is at the join where one swallowed the other, else on the neighbour above. */
private fun mergedOrRemoved(
    blockId: String,
    before: List<ItemListBlock>,
    after: List<ItemListBlock>,
    afterIds: Set<String>,
): CaretTarget? {
    val old = before.associate { it.id to it.value }
    val joined = after.firstOrNull { item -> old[item.id]?.let { it != item.value } == true }
    val removedAt = before.indexOfFirst { it.id !in afterIds }
    val neighbour = after.getOrNull((removedAt - 1).coerceAtLeast(0))
    return when {
        joined != null -> CaretTarget(blockId, joined.id, joinCaret(old.getValue(joined.id), joined.value))
        neighbour != null -> CaretTarget(blockId, neighbour.id, neighbour.value.length)
        else -> null
    }
}

/** The cursor in [now], an item that took the text of the one below onto [was]: at the join. */
private fun joinCaret(was: String, now: String): Int =
    if (now.startsWith(was)) was.length else changedCaret(was, now) ?: now.length

/**
 * Where the cursor goes when block [old] is converted into [new] with the same text: [caret] (in a
 * list, inside its item [itemId]) moved to the same character. Lines of a paragraph are the items of
 * a list, so the cursor keeps its line and column. Null when there is no cursor to carry.
 */
internal fun carriedCaret(old: Block, new: Block, caret: Int?, itemId: String?): CaretTarget? {
    caret ?: return null
    val oldItems = (old as? Block.ListBlock)?.items?.value
    val newItems = (new as? Block.ListBlock)?.items?.value
    return when {
        oldItems != null && newItems != null ->
            itemId?.takeIf { id -> newItems.any { it.id == id } }?.let { CaretTarget(new.id, it, caret) }

        oldItems != null && new is TextualBlock ->
            oldItems.indexOfFirst { it.id == itemId }.takeIf { it >= 0 }?.let { at ->
                val before = oldItems.take(at).sumOf { it.value.length + 1 }
                CaretTarget(new.id, null, before + caret.coerceAtMost(oldItems[at].value.length))
            }

        old is TextualBlock && newItems != null -> lineCaret(new.id, newItems, caret)

        old is TextualBlock && new is TextualBlock -> CaretTarget(new.id, null, caret)

        else -> null
    }
}

/** [caret], an offset in the lines of [items] joined by line breaks, as an item and an offset in it. */
private fun lineCaret(blockId: String, items: List<ItemListBlock>, caret: Int): CaretTarget? {
    var left = caret
    items.forEach { item ->
        if (left <= item.value.length) return CaretTarget(blockId, item.id, left)
        left -= item.value.length + 1
    }
    return items.lastOrNull()?.let { CaretTarget(blockId, it.id, it.value.length) }
}
