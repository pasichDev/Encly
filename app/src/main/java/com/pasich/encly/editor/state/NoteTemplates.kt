package com.pasich.encly.editor.state

import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockFactory
import com.pasich.encly.editor.model.BlockType
import kotlinx.coroutines.flow.MutableStateFlow

// Simple note templates: a starting outline of ordinary blocks (headings, paragraphs, lists)
// for a new, still empty note. They are code and string keys, so they work offline and
// store nothing of their own; a template is just the blocks it makes.

/** One piece of a template. */
sealed interface TemplatePart {
    /** A heading of [type] (H1-H4) reading the string [text]. */
    data class Heading(val type: BlockType, val text: EditorString) : TemplatePart

    /** An empty paragraph to write in. */
    data object Paragraph : TemplatePart

    /** An empty list of [type] (bullets, numbers or checklist). */
    data class ListOf(val type: BlockType) : TemplatePart
}

/** The templates a new note can start from, in the order they are offered. */
enum class NoteTemplate(val label: EditorString, val parts: List<TemplatePart>) {
    /** What a new note is today: nothing. */
    BLANK(EditorString.TEMPLATE_BLANK, emptyList()),

    MEETING(
        EditorString.TEMPLATE_MEETING,
        listOf(
            heading(EditorString.TEMPLATE_ATTENDEES),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
            heading(EditorString.TEMPLATE_AGENDA),
            TemplatePart.ListOf(BlockType.LIST_NUMBER),
            heading(EditorString.TEMPLATE_NOTES),
            TemplatePart.Paragraph,
            heading(EditorString.TEMPLATE_ACTION_ITEMS),
            TemplatePart.ListOf(BlockType.LIST_CHECK),
        ),
    ),

    JOURNAL(
        EditorString.TEMPLATE_JOURNAL,
        listOf(
            heading(EditorString.TEMPLATE_JOURNAL_TODAY),
            TemplatePart.Paragraph,
            heading(EditorString.TEMPLATE_JOURNAL_GRATITUDE),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
            heading(EditorString.TEMPLATE_JOURNAL_TOMORROW),
            TemplatePart.Paragraph,
        ),
    ),

    PROJECT(
        EditorString.TEMPLATE_PROJECT,
        listOf(
            heading(EditorString.TEMPLATE_PROJECT_GOAL),
            TemplatePart.Paragraph,
            heading(EditorString.TEMPLATE_PROJECT_TASKS),
            TemplatePart.ListOf(BlockType.LIST_CHECK),
            heading(EditorString.TEMPLATE_PROJECT_DECISIONS),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
            heading(EditorString.TEMPLATE_PROJECT_QUESTIONS),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
        ),
    ),

    CHECKLIST(
        EditorString.BLOCK_CHECKLIST,
        listOf(
            heading(EditorString.TEMPLATE_CHECKLIST_TODO),
            TemplatePart.ListOf(BlockType.LIST_CHECK),
        ),
    ),
}

private fun heading(text: EditorString) = TemplatePart.Heading(BlockType.H2, text)

/**
 * The blocks of [this] template, headings read by [resolve] (the UI's string lookup, so the
 * text follows the in-app language). Fresh blocks each time; empty for [NoteTemplate.BLANK].
 */
fun NoteTemplate.blocks(resolve: (EditorString) -> String): List<Block> = parts.map { part ->
    when (part) {
        is TemplatePart.Heading ->
            Block.HBlock(text = MutableStateFlow(resolve(part.text)), blockType = part.type)

        TemplatePart.Paragraph -> Block.TextBlock()

        is TemplatePart.ListOf -> BlockFactory.createBlock(part.type)
    }
}

/** Whether the note is exactly what a new note starts as: one empty paragraph. */
fun BlockEditorState.isBlankNote(): Boolean =
    (blocks.singleOrNull() as? Block.TextBlock)?.let { it.text.value.isEmpty() } == true

/**
 * Starts the blank note from [template]: its blocks take the place of the empty paragraph, as
 * one undo step (undo gives the blank note back), and the first empty field after the first
 * heading takes focus. Does nothing, and returns false, unless the note is still blank.
 */
fun BlockEditorState.applyTemplate(template: List<Block>): Boolean {
    if (template.isEmpty() || !isBlankNote()) return false
    batch {
        replaceBlock(0, template.first())
        template.drop(1).forEachIndexed { i, block -> addBlock(1 + i, block) }
    }
    val target = template.indexOfFirst { it.isEmptyField() }.takeIf { it >= 0 } ?: template.lastIndex
    selection.focusAt(target, caret = 0, itemId = template[target].firstItemId())
    return true
}

private fun Block.isEmptyField(): Boolean = when (this) {
    is Block.TextBlock -> text.value.isEmpty()
    is Block.ListBlock -> items.value.all { it.value.isEmpty() }
    else -> false
}
