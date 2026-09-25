package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.flow.MutableStateFlow

// Enter, Backspace and a paste in a list's items: an item splits at the cursor, an empty item
// leaves the list for a paragraph, and an item joins the one above. Each is one undo step.

/**
 * [lineBreak] in item [itemId] of list [listId]. Enter splits the item at the cursor (the text
 * after it goes to a new item); in an empty item it leaves the list. Pasted lines become items.
 * Returns the text the item's field keeps; null when the item is gone.
 */
fun BlockEditorState.listLineBreak(listId: String, itemId: String, lineBreak: LineBreak): String? {
    val item = itemAt(listId, itemId) ?: return null
    return if (lineBreak.isEnter) listEnter(item, lineBreak.head, lineBreak.tail) else listPaste(item, lineBreak)
}

/** Item [at] of [list], the block at [index]. */
private data class ListItemAt(val index: Int, val list: Block.ListBlock, val at: Int)

private fun BlockEditorState.itemAt(listId: String, itemId: String): ListItemAt? {
    val index = indexOf(listId)
    val list = blocks.getOrNull(index) as? Block.ListBlock
    val at = list?.items?.value?.indexOfFirst { it.id == itemId } ?: -1
    return if (list != null && at >= 0) ListItemAt(index, list, at) else null
}

/** Pasted lines in an item: the first joins it, the others become items after it. */
private fun BlockEditorState.listPaste(item: ListItemAt, lineBreak: LineBreak): String {
    val (index, list, at) = item
    val items = list.items.value
    val rest = lineBreak.lines.drop(1)
    val kept = lineBreak.head + lineBreak.lines.first()
    val middle = rest.dropLast(1).filter { it.isNotBlank() }.map(::listItemText)
    val lastLine = listItemText(rest.last())
    val last = lastLine.copy(value = lastLine.value + lineBreak.tail)
    val newItems = items.toMutableList().apply {
        this[at] = items[at].copy(value = kept)
        addAll(at + 1, middle + last)
    }
    batch { setValue(list.id, list.items, newItems) }
    selection.focusAt(index, caret = lastLine.value.length, itemId = last.id)
    return kept
}

private fun BlockEditorState.listEnter(where: ListItemAt, head: String, tail: String): String {
    val (index, list, at) = where
    val items = list.items.value
    val item = items[at]
    return when {
        head.isEmpty() && tail.isEmpty() -> {
            exitList(index, list, at)
            ""
        }

        // At the start of an item: an empty item opens above it.
        head.isEmpty() -> {
            batch { setValue(list.id, list.items, items.toMutableList().apply { add(at, ItemListBlock("")) }) }
            selection.focusAt(index, caret = 0, itemId = item.id)
            tail
        }

        else -> {
            val next = ItemListBlock(tail)
            val newItems = items.toMutableList().apply {
                this[at] = item.copy(value = head)
                add(at + 1, next)
            }
            batch { setValue(list.id, list.items, newItems) }
            selection.focusAt(index, caret = 0, itemId = next.id)
            head
        }
    }
}

/**
 * The empty item [at] leaves the list: a paragraph takes its place, after the list for its last
 * item, before it for its first, and in the middle it splits the list in two around it.
 */
private fun BlockEditorState.exitList(index: Int, list: Block.ListBlock, at: Int) {
    val items = list.items.value
    val before = items.take(at)
    val after = items.drop(at + 1)
    val paragraph = Block.TextBlock()
    batch {
        when {
            before.isEmpty() && after.isEmpty() -> replaceBlock(index, paragraph)

            before.isEmpty() -> {
                setValue(list.id, list.items, after)
                addBlock(index, paragraph)
            }

            else -> {
                setValue(list.id, list.items, before)
                addBlock(index + 1, paragraph)
                if (after.isNotEmpty()) addBlock(index + 2, Block.ListBlock(MutableStateFlow(after), list.blockType))
            }
        }
    }
    selection.focusAt(indexOf(paragraph.id), caret = 0)
}

/**
 * Backspace with the cursor at the start of item [itemId]: the first item leaves the list as a
 * paragraph above it, an empty last item leaves it as a paragraph below, and any other item joins
 * the item above, its text appended there.
 */
fun BlockEditorState.listBackspaceAtStart(listId: String, itemId: String): Boolean {
    val where = itemAt(listId, itemId) ?: return false
    val (index, list, at) = where
    val items = list.items.value
    val item = items[at]
    when {
        at == 0 -> {
            val paragraph = Block.TextBlock(MutableStateFlow(item.value))
            batch {
                if (items.size == 1) {
                    replaceBlock(index, paragraph)
                } else {
                    setValue(list.id, list.items, items.drop(1))
                    addBlock(index, paragraph)
                }
            }
            selection.focusAt(indexOf(paragraph.id), caret = 0)
        }

        item.value.isEmpty() && at == items.lastIndex -> exitList(index, list, at)

        else -> {
            val previous = items[at - 1]
            val newItems = items.toMutableList().apply {
                this[at - 1] = previous.copy(value = previous.value + item.value)
                removeAt(at)
            }
            batch { setValue(list.id, list.items, newItems) }
            selection.focusAt(index, caret = previous.value.length, itemId = previous.id)
        }
    }
    return true
}

/**
 * The [TypingStep] of a list edit from [old] to [new] items: typing in one item, else null (a
 * check, an item added or removed is a step of its own).
 */
fun listTypingStep(old: List<ItemListBlock>, new: List<ItemListBlock>): TypingStep? {
    val sameItems = old.size == new.size &&
        old.indices.all { old[it].id == new[it].id && old[it].isCheck == new[it].isCheck }
    val changed = if (sameItems) old.indices.filter { old[it].value != new[it].value } else emptyList()
    val i = changed.singleOrNull() ?: return null
    return typingStep(old[i].id, old[i].value, new[i].value)
}
