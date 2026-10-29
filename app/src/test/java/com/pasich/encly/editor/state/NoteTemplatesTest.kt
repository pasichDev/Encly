package com.pasich.encly.editor.state

import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ready outlines for a new note: ordinary blocks that the existing serializer stores as usual. */
class NoteTemplatesTest {
    private val editor = BlockEditorState { 0L }

    /** A stand-in for the UI's string lookup: every key reads as its own text. */
    private fun resolve(key: EditorString) = "text#${key.name}"

    @Test
    fun blankIsWhatANewNoteAlreadyIsAndMakesNoBlocks() {
        assertTrue(NoteTemplate.BLANK.blocks(::resolve).isEmpty())
        assertFalse(editor.applyTemplate(NoteTemplate.BLANK.blocks(::resolve)))
        assertTrue(editor.isBlankNote())
    }

    @Test
    fun everyTemplateIsHeadingsParagraphsAndListsOfTheExistingKinds() {
        val allowed = setOf(
            BlockType.H2,
            BlockType.TEXT,
            BlockType.LIST_CHECK,
            BlockType.LIST_BULLET,
            BlockType.LIST_NUMBER,
        )
        NoteTemplate.entries.filter { it != NoteTemplate.BLANK }.forEach { template ->
            val blocks = template.blocks(::resolve)
            assertTrue(template.name, blocks.isNotEmpty())
            assertTrue(template.name, blocks.first() is Block.HBlock)
            blocks.forEach { block ->
                val type = when (block) {
                    is Block.HBlock -> block.blockType
                    is Block.ListBlock -> block.blockType
                    is Block.TextBlock -> BlockType.TEXT
                    else -> error("${template.name} uses ${block::class.simpleName}")
                }
                assertTrue("${template.name}: $type", type in allowed)
            }
            // Headings carry the localized text; every heading is filled in.
            assertTrue(blocks.filterIsInstance<Block.HBlock>().all { it.text.value.startsWith("text#") })
        }
    }

    @Test
    fun everyTemplateSerializesWithTheExistingSerializerAndReadsBackTheSame() {
        NoteTemplate.entries.filter { it != NoteTemplate.BLANK }.forEach { template ->
            val json = BlockConverter.blocksToJson(template.blocks(::resolve))

            val back = BlockConverter.jsonToBlocks(json)

            assertEquals(template.name, json, BlockConverter.blocksToJson(back))
            // Empty paragraphs and lists are not stored; the headings are.
            val headings = template.parts.filterIsInstance<TemplatePart.Heading>().size
            assertEquals(template.name, headings, back.filterIsInstance<Block.HBlock>().size)
        }
    }

    @Test
    fun aTemplateFillsABlankNoteInOneUndoStepAndUndoGivesTheBlankNoteBack() {
        val blocks = NoteTemplate.MEETING.blocks(::resolve)

        assertTrue(editor.applyTemplate(blocks))

        assertEquals(blocks.size, editor.blocks.size)
        assertFalse(editor.isBlankNote())
        editor.undo()
        assertTrue(editor.isBlankNote())
        assertFalse(editor.canUndo.value)
        editor.redo()
        assertEquals(blocks.size, editor.blocks.size)
    }

    @Test
    fun aTemplateNeverReplacesWhatIsAlreadyWritten() {
        editor.load(listOf(Block.TextBlock(MutableStateFlow("my note"))))

        assertFalse(editor.applyTemplate(NoteTemplate.JOURNAL.blocks(::resolve)))

        assertEquals("my note", (editor.blocks.single() as Block.TextBlock).text.value)
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun theFirstEmptyFieldAfterTheFirstHeadingTakesFocus() {
        val blocks = NoteTemplate.PROJECT.blocks(::resolve)
        editor.applyTemplate(blocks)

        var request: FocusRequest? = null
        runTest { request = editor.selection.requests.first() }

        assertNotNull(request)
        assertEquals(blocks[1].id, request?.blockId)
    }
}
