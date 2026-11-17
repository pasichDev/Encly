package com.pasich.encly.editor.state

import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            assertTrue(blocks.isNotEmpty(), template.name)
            assertTrue(blocks.first() is Block.HBlock, template.name)
            blocks.forEach { block ->
                val type = when (block) {
                    is Block.HBlock -> block.blockType
                    is Block.ListBlock -> block.blockType
                    is Block.TextBlock -> BlockType.TEXT
                    else -> error("${template.name} uses ${block::class.simpleName}")
                }
                assertTrue(type in allowed, "${template.name}: $type")
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

            assertEquals(json, BlockConverter.blocksToJson(back), template.name)
            // Empty paragraphs and lists are not stored; the headings are.
            val headings = template.parts.filterIsInstance<TemplatePart.Heading>().size
            assertEquals(headings, back.filterIsInstance<Block.HBlock>().size, template.name)
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

    @Test
    fun aTemplateAsAppliedIsUntouchedAndABlankOrLoadedNoteIsNot() {
        assertFalse(editor.isUntouchedTemplate())

        editor.applyTemplate(NoteTemplate.MEETING.blocks(::resolve))
        assertTrue(editor.isUntouchedTemplate())

        editor.undo()
        assertFalse(editor.isUntouchedTemplate())
        editor.redo()
        assertTrue(editor.isUntouchedTemplate())

        editor.load(NoteTemplate.MEETING.blocks(::resolve))
        assertFalse(editor.isUntouchedTemplate())
    }

    @Test
    fun anyEditOfAnAppliedTemplateMakesItATouchedNote() {
        fun fresh(): BlockEditorState = BlockEditorState { 0L }.also {
            it.applyTemplate(NoteTemplate.MEETING.blocks(::resolve))
        }

        // Typing into a heading, a paragraph, a list row.
        fresh().let { e ->
            (e.blocks.first() as Block.HBlock).text.value += "!"
            assertFalse(e.isUntouchedTemplate())
        }
        fresh().let { e ->
            (e.blocks.filterIsInstance<Block.TextBlock>().first()).text.value = "x"
            assertFalse(e.isUntouchedTemplate())
        }
        fresh().let { e ->
            val list = e.blocks.filterIsInstance<Block.ListBlock>().first()
            list.items.value = listOf(list.items.value.first().copy(value = "a"))
            assertFalse(e.isUntouchedTemplate())
        }
        // Checking an item, locking a checklist, a list row added.
        fresh().let { e ->
            val list = e.blocks.filterIsInstance<Block.ListBlock>().last()
            list.items.value = listOf(list.items.value.first().copy(isCheck = true))
            assertFalse(e.isUntouchedTemplate())
        }
        fresh().let { e ->
            e.blocks.filterIsInstance<Block.ListBlock>().last().locked.value = true
            assertFalse(e.isUntouchedTemplate())
        }
        fresh().let { e ->
            val list = e.blocks.filterIsInstance<Block.ListBlock>().first()
            list.items.value = list.items.value + list.items.value.first().copy(id = "new")
            assertFalse(e.isUntouchedTemplate())
        }
        // A block added, or one of another kind in its place.
        fresh().let { e ->
            e.addBlock(Block.TextBlock())
            assertFalse(e.isUntouchedTemplate())
        }
        fresh().let { e ->
            e.replaceBlock(1, Block.QuoteBlock())
            assertFalse(e.isUntouchedTemplate())
        }
    }

    @Test
    fun typingAndDeletingBackToTheTemplateTextIsUntouchedAgain() {
        editor.applyTemplate(NoteTemplate.JOURNAL.blocks(::resolve))
        val paragraph = editor.blocks.filterIsInstance<Block.TextBlock>().first()

        paragraph.text.value = "x"
        assertFalse(editor.isUntouchedTemplate())
        paragraph.text.value = ""
        assertTrue(editor.isUntouchedTemplate())
    }
}
