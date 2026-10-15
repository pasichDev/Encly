package com.pasich.encly.presentation.editor.state

import androidx.annotation.StringRes
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.factory.BlockFactory
import kotlinx.coroutines.flow.MutableStateFlow

// Simple note templates: a starting outline of ordinary blocks (headings, paragraphs, lists)
// for a new, still empty note. They are code and string resources, so they work offline and
// store nothing of their own; a template is just the blocks it makes.

/** One piece of a template. */
sealed interface TemplatePart {
    /** A heading of [type] (H1-H4) reading the string resource [text]. */
    data class Heading(val type: BlockType, @param:StringRes val text: Int) : TemplatePart

    /** An empty paragraph to write in. */
    data object Paragraph : TemplatePart

    /** An empty list of [type] (bullets, numbers or checklist). */
    data class ListOf(val type: BlockType) : TemplatePart
}

/** The templates a new note can start from, in the order they are offered. */
enum class NoteTemplate(@param:StringRes val label: Int, val parts: List<TemplatePart>) {
    /** What a new note is today: nothing. */
    BLANK(R.string.template_blank, emptyList()),

    MEETING(
        R.string.template_meeting,
        listOf(
            heading(R.string.template_attendees),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
            heading(R.string.template_agenda),
            TemplatePart.ListOf(BlockType.LIST_NUMBER),
            heading(R.string.template_notes),
            TemplatePart.Paragraph,
            heading(R.string.template_action_items),
            TemplatePart.ListOf(BlockType.LIST_CHECK),
        ),
    ),

    JOURNAL(
        R.string.template_journal,
        listOf(
            heading(R.string.template_journal_today),
            TemplatePart.Paragraph,
            heading(R.string.template_journal_gratitude),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
            heading(R.string.template_journal_tomorrow),
            TemplatePart.Paragraph,
        ),
    ),

    PROJECT(
        R.string.template_project,
        listOf(
            heading(R.string.template_project_goal),
            TemplatePart.Paragraph,
            heading(R.string.template_project_tasks),
            TemplatePart.ListOf(BlockType.LIST_CHECK),
            heading(R.string.template_project_decisions),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
            heading(R.string.template_project_questions),
            TemplatePart.ListOf(BlockType.LIST_BULLET),
        ),
    ),

    CHECKLIST(
        R.string.block_checklist,
        listOf(
            heading(R.string.template_checklist_todo),
            TemplatePart.ListOf(BlockType.LIST_CHECK),
        ),
    ),
}

private fun heading(@StringRes text: Int) = TemplatePart.Heading(BlockType.H2, text)

/**
 * The blocks of [this] template, headings read by [resolve] (the UI's string lookup, so the
 * text follows the in-app language). Fresh blocks each time; empty for [NoteTemplate.BLANK].
 */
fun NoteTemplate.blocks(resolve: (Int) -> String): List<Block> = parts.map { part ->
    when (part) {
        is TemplatePart.Heading ->
            Block.HBlock(text = MutableStateFlow(resolve(part.text)), blockType = part.type)

        TemplatePart.Paragraph -> Block.TextBlock()

        is TemplatePart.ListOf -> BlockFactory.createBlock(part.type)
    }
}

/** Whether the note is exactly what a new note starts as: one empty paragraph. */
fun BlockEditorState.isBlankNote(): Boolean =
    (blocks.singleOrNull() as? Block.TextBlock)?.text?.value?.isEmpty() == true

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
    val item = (template[target] as? Block.ListBlock)?.items?.value?.firstOrNull()
    selection.focusAt(target, caret = 0, itemId = item?.id)
    return true
}

private fun Block.isEmptyField(): Boolean = when (this) {
    is Block.TextBlock -> text.value.isEmpty()
    is Block.ListBlock -> items.value.all { it.value.isEmpty() }
    else -> false
}
