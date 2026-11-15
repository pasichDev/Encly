package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.MAX_LIST_LEVEL
import com.pasich.encly.editor.model.alongside
import com.pasich.encly.editor.model.kindIn
import com.pasich.encly.editor.model.normalizedLevels
import kotlinx.coroutines.flow.MutableStateFlow

// Enter, Backspace and a paste in a list's items: an item splits at the cursor, an empty item
// leaves the list for a paragraph, and an item joins the one above. Each is one undo step.

/**
 * [lineBreak] in item [itemId] of list [listId]. Enter splits the item at the cursor (the text
 * after it goes to a new item); in an empty item it leaves the list. Pasted lines become items.
 * Returns the text the item's field keeps; null when the item is gone.
 */
public fun BlockEditorState.listLineBreak(listId: String, itemId: String, lineBreak: LineBreak): String? {
    val item = itemAt(listId, itemId) ?: return null
    return if (lineBreak.isEnter) {
        listEnter(item, lineBreak.head, lineBreak.tail)
    } else {
        listPasteText(item, lineBreak.head, lineBreak.lines, lineBreak.tail)
    }
}

/** Item [at] of [list], the block at [index]. */
internal data class ListItemAt(val index: Int, val list: Block.ListBlock, val at: Int)

internal fun BlockEditorState.itemAt(listId: String, itemId: String): ListItemAt? {
    val index = indexOf(listId)
    val list = blocks.getOrNull(index) as? Block.ListBlock
    val at = list?.let { l -> l.items.value.indexOfFirst { it.id == itemId } } ?: -1
    return if (list != null && at >= 0) ListItemAt(index, list, at) else null
}

/**
 * Pasted [lines] in an item, after its text [head] and before its [tail]: the first joins it, the others become
 * items after it. One line stays in the item. Blank lines at either end are dropped, so a copied line that ends in a
 * line break stays in the item. In an empty item the first line is read like the others (its list mark is not kept as
 * text); after text it joins the text as typed.
 */
internal fun BlockEditorState.listPasteText(item: ListItemAt, head: String, lines: List<String>, tail: String): String {
    val (index, list, at) = item
    val items = list.items.value
    val here = items[at]
    // Blank lines at the end are only the copied text's line break. At the start they are dropped in an empty item;
    // after text a leading line break is the user's: the pasted lines start on a new item.
    val pasted = if (head.isBlank()) trimBlankLines(lines) else lines.dropLastWhile { it.isBlank() }
    if (pasted.isEmpty()) {
        selection.focusAt(index, caret = head.length, itemId = here.id)
        return head + tail
    }
    // In an empty item the first line is read like the others (its mark is no text); after text it joins as typed.
    val first = if (head.isBlank()) listItemText(pasted.first()) else null
    val kept = head + (first?.value ?: pasted.first())
    // A ticked or open mark in the pasted first line sets the state of a checklist item it lands in.
    val ticked = first != null && here.kindIn(list.blockType) == BlockType.LIST_CHECK
    val isCheck = if (ticked) first.isCheck else here.isCheck
    val rest = pasted.drop(1)
    val newItems = items.toMutableList()
    val whole: String
    val caretItem: ItemListBlock
    var caret = kept.length
    if (rest.isEmpty()) {
        whole = kept + tail
        newItems[at] = here.copy(value = whole, isCheck = isCheck)
        caretItem = newItems[at]
    } else {
        // Pasted lines go in at the level of the item they are pasted into, nested as deep under it as they were
        // under the first line.
        val base = if (first != null) indentLevel(pasted.first()) else 0
        val placed = rest.filterIndexed { i, line -> i == rest.lastIndex || line.isNotBlank() }
            .map { line -> placedAlongside(here, listItemText(line), base) }
        val lastLine = placed.last()
        caretItem = lastLine.copy(value = lastLine.value + tail)
        caret = lastLine.value.length
        whole = kept
        newItems[at] = here.copy(value = kept, isCheck = isCheck)
        newItems.addAll(at + 1, placed.dropLast(1) + caretItem)
    }
    batch { setValue(list.id, list.items, newItems.normalizedLevels(list.blockType)) }
    selection.focusAt(index, caret = caret, itemId = caretItem.id)
    return whole
}

/** The pasted item [read] placed under [here]: at its level, nested as deep as it was nested past [base] levels. */
private fun placedAlongside(here: ItemListBlock, read: ItemListBlock, base: Int): ItemListBlock {
    val deeper = (read.level - base).coerceAtLeast(0)
    return read.alongside(here).copy(level = (here.level + deeper).coerceAtMost(MAX_LIST_LEVEL))
}

private fun BlockEditorState.listEnter(where: ListItemAt, head: String, tail: String): String {
    val (index, list, at) = where
    val items = list.items.value
    val item = items[at]
    return when {
        // An empty nested item comes out one level first; an empty item at the top leaves the list.
        head.isEmpty() && tail.isEmpty() -> {
            if (items.normalizedLevels(list.blockType)[at].level > 0) {
                outdentEmpty(index, list, at)
            } else {
                exitList(index, list, at, from = CaretTarget(list.id, item.id, 0))
            }
            ""
        }

        // At the start of an item: an empty item opens above it.
        head.isEmpty() -> {
            val above = ItemListBlock("").alongside(item)
            batch { setValue(list.id, list.items, items.toMutableList().apply { add(at, above) }) }
            CaretTarget(list.id, item.id, 0).let { attachCaretToLastStep(it, it) }
            selection.focusAt(index, caret = 0, itemId = item.id)
            tail
        }

        else -> {
            val next = ItemListBlock(tail).alongside(item)
            val newItems = items.toMutableList().apply {
                this[at] = item.copy(value = head)
                add(at + 1, next)
            }
            batch { setValue(list.id, list.items, newItems) }
            attachCaretToLastStep(CaretTarget(list.id, item.id, head.length), CaretTarget(list.id, next.id, 0))
            selection.focusAt(index, caret = 0, itemId = next.id)
            head
        }
    }
}

/** The empty nested item [at] comes out one level (with its children), the cursor staying in it. */
private fun BlockEditorState.outdentEmpty(index: Int, list: Block.ListBlock, at: Int) {
    val item = list.items.value[at]
    shiftListItem(list.id, item.id, deeper = false)
    selection.focusAt(index, caret = 0, itemId = item.id)
}

/**
 * The empty item [at] leaves the list: a paragraph takes its place, after the list for its last
 * item, before it for its first, and in the middle it splits the list in two around it.
 */
private fun BlockEditorState.exitList(index: Int, list: Block.ListBlock, at: Int, from: CaretTarget) {
    val items = list.items.value
    val before = items.take(at)
    val after = items.drop(at + 1).normalizedLevels(list.blockType)
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
    attachCaretToLastStep(from, CaretTarget(paragraph.id, null, 0))
    selection.focusAt(indexOf(paragraph.id), caret = 0)
}

/**
 * Backspace with the cursor at the start of item [itemId]: a nested item comes out one level first, the
 * first item leaves the list as a paragraph above it, and any other item, empty or not, joins the item
 * above (see [joinIntoCurrent]).
 */
public fun BlockEditorState.listBackspaceAtStart(listId: String, itemId: String): Boolean {
    val where = itemAt(listId, itemId) ?: return false
    val (index, list, at) = where
    val items = list.items.value
    val item = items[at]
    when {
        items.normalizedLevels(list.blockType)[at].level > 0 -> {
            shiftListItem(list.id, item.id, deeper = false)
            selection.focusAt(index, caret = 0, itemId = item.id)
        }

        at == 0 -> {
            val paragraph = Block.TextBlock(MutableStateFlow(item.value))
            batch {
                if (items.size == 1) {
                    replaceBlock(index, paragraph)
                } else {
                    setValue(list.id, list.items, items.drop(1).normalizedLevels(list.blockType))
                    addBlock(index, paragraph)
                }
            }
            attachCaretToLastStep(CaretTarget(list.id, item.id, 0), CaretTarget(paragraph.id, null, 0))
            selection.focusAt(indexOf(paragraph.id), caret = 0)
        }

        else -> joinIntoCurrent(where)
    }
    return true
}

/**
 * Item [where] and the one above it become one: the item above is removed and its text (and its
 * ticked state) goes in front of the item's own, which keeps the focus and the keyboard. Backspace held
 * down then goes on deleting, item after item, in the field that has it: when the focus moves to another
 * field the keyboard starts a new input and stops repeating the key. One undo step.
 */
private fun BlockEditorState.joinIntoCurrent(where: ListItemAt) {
    val (index, list, at) = where
    val items = list.items.value
    val item = items[at]
    val previous = items[at - 1]
    val join = previous.value.length
    val newItems = items.toMutableList().apply {
        this[at] = item.copy(
            value = previous.value + item.value,
            isCheck = previous.isCheck,
            level = previous.level,
            kind = previous.kind,
        )
        removeAt(at - 1)
    }
    batch { setValue(list.id, list.items, newItems.normalizedLevels(list.blockType)) }
    attachCaretToLastStep(CaretTarget(list.id, item.id, 0), CaretTarget(list.id, item.id, join))
    selection.focusAt(index, caret = join, itemId = item.id)
}

/**
 * The [TypingStep] of a list edit from [old] to [new] items: typing in one item, else null (a
 * check, an item added or removed is a step of its own).
 */
public fun listTypingStep(old: List<ItemListBlock>, new: List<ItemListBlock>): TypingStep? {
    val sameItems = old.size == new.size &&
        old.indices.all { old[it].id == new[it].id && old[it].isCheck == new[it].isCheck }
    val changed = if (sameItems) old.indices.filter { old[it].value != new[it].value } else emptyList()
    val i = changed.singleOrNull() ?: return null
    return typingStep(old[i].id, old[i].value, new[i].value)
}
