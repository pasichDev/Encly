package com.pasich.encly.editor.state

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.codec.BlockJson
import com.pasich.encly.editor.codec.InlineHtml
import com.pasich.encly.editor.codec.MarkdownBlockMapper
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.BlockUtils
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.theme.EditorLineSpacing
import com.pasich.encly.editor.theme.frameInset
import com.pasich.encly.editor.theme.isCompact
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The less travelled direction of every condition in the editor's state: out-of-range, missing, equal, empty. */
@Suppress("LargeClass", "TooManyFunctions")
class BranchGapsTest {
    private fun text(value: String = "") = Block.TextBlock(MutableStateFlow(value))

    private fun list(vararg values: String, type: BlockType = BlockType.LIST_BULLET) =
        Block.ListBlock(MutableStateFlow(values.map { ItemListBlock(it) }), type)

    private fun editorOf(vararg blocks: Block) = BlockEditorState { 0L }.also { it.load(blocks.toList()) }

    private fun enter(head: String = "", tail: String = "") = LineBreak(head, listOf("", ""), tail)

    private fun items(editor: BlockEditorState, index: Int) = (editor.blocks[index] as Block.ListBlock).items.value

    // --- model and theme ------------------------------------------------------------------------

    @Test
    fun whichBlocksCountAsEmpty() {
        assertTrue(BlockUtils.isBlockEmpty(Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)))
        assertTrue(BlockUtils.isBlockEmpty(list("")))
        assertFalse(BlockUtils.isBlockEmpty(list("a")))
        assertFalse(BlockUtils.isBlockEmpty(list("", "")))
        assertTrue(BlockUtils.isBlockEmpty(Block.LinkBlock()))
        assertFalse(BlockUtils.isBlockEmpty(Block.SeparatorBlock()))
    }

    @Test
    fun spacingKnowsItsCompactEnd() {
        assertFalse(EditorLineSpacing.DEFAULT.isCompact)
        assertTrue(EditorLineSpacing.COMPACT.isCompact)
        assertEquals(0.dp, EditorLineSpacing.COMPACT.frameInset)
        assertEquals(4.dp, EditorLineSpacing.DEFAULT.frameInset)
    }

    // --- the undo history -------------------------------------------------------------------------

    private val history = SnapshotStateList<Block>().apply {
        add(text("a"))
        add(text("b"))
    }
    private var now = 0L
    private val ops = BlockOperations(history) { now }

    private fun undoSteps(): Int {
        var steps = 0
        while (ops.undo()) steps++
        return steps
    }

    @Test
    fun operationsOutsideTheListChangeNothing() {
        val extra = text("x")
        ops.addBlock(-1, extra)
        ops.addBlock(3, extra)
        ops.removeBlock(-1)
        ops.removeBlock(2)
        ops.moveBlock(-1, 0)
        ops.moveBlock(2, 0)
        ops.moveBlock(0, -1)
        ops.moveBlock(0, 2)
        ops.replaceBlock(-1, extra)
        ops.replaceBlock(2, extra)
        ops.batch {
            addBlock(-1, extra)
            addBlock(3, extra)
            removeBlock(-1)
            removeBlock(2)
            replaceBlock(-1, extra)
            replaceBlock(2, extra)
        }

        assertEquals(2, history.size)
        assertFalse(ops.canUndo())
    }

    @Test
    fun anEditToTheSameValueIsNotAStep() {
        val field = MutableStateFlow("same")
        ops.changeValue("id", field, "same", mergeable = true)
        ops.batch { setValue("id", field, "same") }
        assertFalse(ops.canUndo())
    }

    @Test
    fun valuesThatAreNotTextHaveNoTypingStep() {
        val flag = MutableStateFlow(false)
        ops.changeValue("id", flag, true, mergeable = true)
        ops.changeValue("id", flag, false, mergeable = true)
        ops.changeValue(
            "id",
            MutableStateFlow("a"),
            "ab",
            mergeable = true,
            step = TypingStep("id", TypingKind.INSERT, false),
        )
        assertTrue(undoSteps() >= 2)
    }

    @Test
    fun typingMergesOnlyInTheSameFieldWithinTheWindow() {
        val field = MutableStateFlow("")
        val other = MutableStateFlow("")
        ops.changeValue("a", field, "x", mergeable = true)
        ops.changeValue("a", field, "xy", mergeable = true)
        assertEquals(1, undoSteps())

        ops.clear()
        ops.changeValue("a", field, "1", mergeable = true)
        ops.changeValue("b", field, "12", mergeable = true)
        assertEquals(2, undoSteps())

        ops.clear()
        ops.changeValue("a", field, "p", mergeable = true)
        ops.changeValue("a", other, "q", mergeable = true)
        assertEquals(2, undoSteps())

        ops.clear()
        ops.changeValue("a", field, "r", mergeable = true)
        now += BlockOperations.MERGE_WINDOW_MS + 1
        ops.changeValue("a", field, "rs", mergeable = true)
        assertEquals(2, undoSteps())
    }

    @Test
    fun anEditThatIsNotTypingNeverMergesAndTypingKindsDoNot() {
        val field = MutableStateFlow("")
        ops.changeValue("a", field, "one", mergeable = false)
        ops.changeValue("a", field, "one two", mergeable = true)
        assertEquals(2, undoSteps())

        ops.clear()
        ops.changeValue("a", field, "abc", mergeable = true)
        ops.changeValue("a", field, "ab", mergeable = true)
        assertEquals(2, undoSteps())
    }

    @Test
    fun theCursorOfAStepIsRememberedOncePerStep() {
        val target = CaretTarget("a", null, 1)
        val step = BlockOperations.CaretStep(target, target)
        ops.attachCaret(step)
        assertNull(ops.lastMovedCaret())

        ops.addBlock(0, text("new"))
        ops.attachCaret(BlockOperations.CaretStep(target, target))
        ops.attachCaret(step)
        ops.undo()
        assertEquals(step, ops.lastMovedCaret())

        ops.redo()
        ops.addBlock(0, text("another"))
        ops.undo()
        assertNull(ops.lastMovedCaret())
    }

    // --- the editor state -------------------------------------------------------------------------

    @Test
    fun removingFollowsItsActionsAndKnowsWhatIsThere() {
        val editor = editorOf(list("a", "b"), text("x"))
        editor.removeBlock(text("unknown"), BlockRemoveAction.REMOVE)
        assertEquals(2, editor.blocks.size)
        editor.removeBlock(editor.blocks[1], BlockRemoveAction.REMOVE_BACKSPACE)
        assertEquals(2, editor.blocks.size)
        editor.removeBlock(editor.blocks[0], BlockRemoveAction.REMOVE_BACKSPACE_LIST)
        assertEquals(1, editor.blocks.size)

        val single = editorOf(text())
        single.removeBlock(single.blocks[0], BlockRemoveAction.REMOVE_BACKSPACE)
        assertEquals(1, single.blocks.size)
    }

    @Test
    fun replacingAndMovingOutsideTheNoteDoNothing() {
        val editor = editorOf(text("a"), text("b"))
        assertFalse(editor.replaceBlock(-1, text()))
        assertFalse(editor.replaceBlock(2, text()))
        editor.moveBlock(-1, 0)
        editor.moveBlock(2, 0)
        editor.moveBlock(0, -1)
        editor.moveBlock(0, 2)
        assertEquals(listOf("a", "b"), editor.blocks.map { (it as Block.TextBlock).text.value })
    }

    @Test
    fun addingReplacesOnlyAnEmptyFirstParagraph() {
        val empty = editorOf(text())
        empty.addBlock(list(""))
        assertEquals(1, empty.blocks.size)

        val filled = editorOf(text("x"))
        filled.addBlock(list(""))
        assertEquals(2, filled.blocks.size)

        val separator = editorOf(Block.SeparatorBlock())
        separator.addBlock(list(""))
        assertEquals(2, separator.blocks.size)

        val second = editorOf(text(), text())
        second.selection.onInteraction(second.blocks[1].id)
        second.addBlock(list(""))
        assertEquals(3, second.blocks.size)
    }

    @Test
    fun undoAfterTheBlockLeftTheNoteKeepsTheFocusAbove() {
        val first = text("a")
        val second = text()
        val editor = editorOf(first, second)
        editor.selection.onFocused(second.id)
        editor.changeValue(second.id, second.text, "hello", mergeable = false)
        (editor.blocks as MutableList<Block>).remove(second)

        editor.undo()

        assertEquals(first.id, editor.blocks.single().id)
    }

    @Test
    fun undoWhereTheFieldCanNoLongerTakeFocusFallsBack() {
        val checks = list("a", type = BlockType.LIST_CHECK)
        val editor = editorOf(text("t"), checks)
        editor.selection.onFocused(checks.id)
        editor.changeValue(checks.id, checks.items, checks.items.value.map { it.copy(value = "ab") }, mergeable = false)
        editor.undo()
        checks.locked.value = true

        editor.redo()

        assertEquals("ab", checks.items.value.single().value)
    }

    @Test
    fun undoOfAStepWithoutTextKeepsTheCursorWhereItWas() {
        val checks = list("a", "b", type = BlockType.LIST_CHECK)
        val editor = editorOf(checks)
        val second = checks.items.value[1]
        editor.selection.onItemCaret(checks.id, second.id, 1)
        editor.selection.onFocused(checks.id)
        editor.changeValue(
            checks.id,
            checks.items,
            checks.items.value.map {
                it.copy(isCheck = true)
            },
            mergeable = false,
        )
        editor.undo()
        editor.redo()

        editor.selection.onItemCaret(checks.id, "ghost", 1)
        editor.undo()

        val paragraph = text("p")
        val plain = editorOf(paragraph)
        plain.selection.onFocused(paragraph.id)
        plain.selection.onCaret(paragraph.id, 1)
        plain.batch { addBlock(1, text("next")) }
        plain.undo()
        assertEquals(1, plain.blocks.size)
    }

    // --- Smart Paste and text diffs ---------------------------------------------------------------

    @Test
    fun diffsFallBackToTheSmallestRangeWhenTheCursorDoesNotFit() {
        assertEquals(TextDiff(4, "", "XY"), textDiff("abcd", "abcdXY", cursor = 1))
        assertEquals(TextDiff(2, "", "c"), textDiff("ab", "abc", cursor = 10))
        assertEquals(TextDiff(2, "c", ""), textDiff("abc", "ab", cursor = 2))
        assertNotNull(textDiff("abc", "zbXYc", cursor = 4))
        assertNotNull(textDiff("abc", "aXbcY", cursor = 2))
    }

    @Test
    fun aLineBreakNeedsTwoLines() {
        assertThrows(IllegalArgumentException::class.java) { LineBreak("", listOf("x"), "") }
    }

    @Test
    fun pastedLinesAreReadWithOrWithoutTextAroundThem() {
        val lines = listOf("word", "")
        for ((head, tail) in listOf("" to "", "" to "t", "h" to "")) {
            val paragraph = text(head + tail)
            val editor = editorOf(paragraph)
            editor.breakLine(paragraph.id, LineBreak(head, lines, tail))
            assertTrue(editor.blocks.isNotEmpty())
        }
    }

    @Test
    fun theCommandPickerIgnoresWhatHasNothingToMove() {
        val picker = SlashPicker()
        picker.commit()
        picker.moveHighlight(1)
        assertNull(picker.menu.value)

        picker.setLabels(mapOf(SlashCommand.TEXT to "Zorp"))
        picker.open("p")
        picker.onText("p", "/zorp")
        assertEquals(listOf(SlashCommand.TEXT), picker.menu.value?.matches)
        picker.setLabels(mapOf(SlashCommand.TEXT to "Text"))
        assertTrue(picker.menu.value?.matches.orEmpty().isEmpty())
        picker.moveHighlight(1)
        assertEquals(0, picker.menu.value?.highlighted)
    }

    @Test
    fun aTemplateFocusesTheFirstEmptyFieldOrTheLastBlock() {
        val heading = { Block.HBlock(MutableStateFlow("h"), BlockType.H2) }
        val filledOnly = editorOf(text())
        assertTrue(filledOnly.applyTemplate(listOf(heading(), text("filled"))))
        assertEquals(1, filledOnly.selection.focusedIndex)

        val emptyText = editorOf(text())
        emptyText.applyTemplate(listOf(heading(), text()))
        assertEquals(1, emptyText.selection.focusedIndex)

        val filledList = editorOf(text())
        filledList.applyTemplate(listOf(heading(), list("x")))
        assertEquals(1, filledList.selection.focusedIndex)

        val noItems = editorOf(text())
        noItems.applyTemplate(listOf(heading(), Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)))
        assertEquals(1, noItems.selection.focusedIndex)

        assertFalse(editorOf(text("x")).isBlankNote())
        assertFalse(editorOf(text(), text()).isBlankNote())
        assertFalse(editorOf(Block.SeparatorBlock()).isBlankNote())
    }

    // --- tools ------------------------------------------------------------------------------------

    @Test
    fun theKeyboardReturnsToTheCursorOfAParagraphButNotOfAList() {
        val paragraph = text("abc")
        val editor = editorOf(paragraph)
        editor.focusWorkingBlock()
        editor.selection.onCaret(paragraph.id, 2)
        editor.focusWorkingBlock()

        val bullets = list("a")
        val withList = editorOf(bullets)
        withList.selection.onFocused(bullets.id)
        withList.focusWorkingBlock()
        assertEquals(0, withList.selection.focusedIndex)
    }

    @Test
    fun onlyAChecklistCanBeLocked() {
        val bullets = list("a")
        val paragraph = text("p")
        val checks = list("c", type = BlockType.LIST_CHECK)
        val editor = editorOf(bullets, paragraph, checks)
        assertFalse(editor.toggleChecklistLock(bullets.id))
        assertFalse(editor.toggleChecklistLock(paragraph.id))
        assertFalse(editor.toggleChecklistLock("unknown"))
        assertTrue(editor.toggleChecklistLock(checks.id))
    }

    @Test
    fun aToolOnALockedChecklistChangesNothing() {
        val checks = list("c", type = BlockType.LIST_CHECK)
        val editor = editorOf(checks)
        editor.toggleChecklistLock(checks.id)
        editor.selection.onInteraction(checks.id)
        editor.applyTool(BlockType.QUOTE)
        assertTrue(editor.blocks.single() is Block.ListBlock)

        val plain = editorOf(list("b"), text("p"))
        plain.selection.onInteraction(plain.blocks[1].id)
        plain.applyTool(BlockType.SEPARATOR)
        assertTrue(plain.blocks.any { it is Block.SeparatorBlock })
        plain.selection.onInteraction(plain.blocks[0].id)
        plain.applyTool(BlockType.SEPARATOR)
        assertTrue(plain.blocks.count { it is Block.SeparatorBlock } == 2)
    }

    @Test
    fun everyToolConvertsATextBlock() {
        for (tool in BlockType.entries) {
            val paragraph = text("x\ny")
            val converted = convertBlock(paragraph, tool)
            if (tool == BlockType.TEXT || tool == BlockType.SEPARATOR || tool == BlockType.LINK) {
                assertNull(tool.name, converted)
            } else {
                assertNotNull(tool.name, converted)
            }
            convertBlock(Block.HBlock(MutableStateFlow("h"), BlockType.H3), tool)
            convertBlock(list("a", "b"), tool)
            convertBlock(Block.QuoteBlock(MutableStateFlow("")), tool)
        }
        assertTrue(convertBlock(Block.QuoteBlock(MutableStateFlow("")), BlockType.LINK) == null)
    }

    @Test
    fun theToolbarKnowsItsEdges() {
        val editor = editorOf(text("a"), text("b"))
        editor.selection.onInteraction(editor.blocks[0].id)
        assertFalse(editor.canMoveInteracted(up = true))
        editor.moveInteracted(up = true)
        assertTrue(editor.canMoveInteracted(up = false))
        editor.selection.onInteraction(editor.blocks[1].id)
        assertFalse(editor.canMoveInteracted(up = false))
        editor.moveInteracted(up = false)
        editor.moveBlockOf("unknown", up = true)
        editor.moveBlockOf(editor.blocks[0].id, up = true)
        editor.moveBlockOf(editor.blocks[1].id, up = false)
        assertTrue(editor.canRemoveInteracted())
        editor.removeInteracted()
        assertFalse(editor.canRemoveInteracted())
        editor.removeInteracted()
        editor.duplicateInteracted()
        assertFalse(editor.duplicateBlockOf("unknown"))
    }

    // --- Enter and Backspace ----------------------------------------------------------------------

    @Test
    fun backspaceAtTheStartOfAHeadingOrAQuoteTurnsItIntoAParagraph() {
        val heading = Block.HBlock(MutableStateFlow("h"), BlockType.H1)
        val quote = Block.QuoteBlock(MutableStateFlow("q"))
        val editor = editorOf(heading, quote)
        assertTrue(editor.backspaceAtStart(heading.id))
        assertTrue(editor.backspaceAtStart(quote.id))
        assertTrue(editor.blocks.all { it is Block.TextBlock })
    }

    @Test
    fun aParagraphBelowAnEmptyListJoinsIt() {
        val empty = Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)
        val paragraph = text("tail")
        val editor = editorOf(empty, paragraph)

        assertTrue(editor.backspaceAtStart(paragraph.id))

        assertEquals("tail", empty.items.value.single().value)
    }

    @Test
    fun anAddressIsPastedOnlyIntoAnEmptyParagraphThatIsThere() {
        val link = com.pasich.encly.editor.model.LinkDataBlock(url = "https://x.org")
        val unknown = editorOf(text())
        assertFalse(unknown.pasteLink("unknown", "https://x.org", link))
        val filled = text("x")
        assertFalse(editorOf(filled).pasteLink(filled.id, "https://x.org", link))
        val withNext = text()
        val next = text("next")
        val editor = editorOf(withNext, next)
        assertTrue(editor.pasteLink(withNext.id, "https://x.org", link))
        assertEquals(2, editor.blocks.size)
    }

    @Test
    fun listEditingKnowsWhatIsNotThere() {
        val bullets = list("a", "", "c")
        val paragraph = text("p")
        val editor = editorOf(bullets, paragraph)
        assertNull(editor.listLineBreak(paragraph.id, "x", enter()))
        assertNull(editor.listLineBreak(bullets.id, "unknown", enter()))
        assertFalse(editor.listBackspaceAtStart(paragraph.id, "x"))
        assertFalse(editor.listBackspaceAtStart(bullets.id, "unknown"))
        // A middle empty item joins the one above; a non-empty last item does too.
        assertTrue(editor.listBackspaceAtStart(bullets.id, bullets.items.value[1].id))
        assertTrue(editor.listBackspaceAtStart(bullets.id, bullets.items.value[1].id))
        assertEquals(listOf("ac"), bullets.items.value.map { it.value })
    }

    @Test
    fun aTypingStepIsOnlyOneItemTyped() {
        val a = ItemListBlock("a", id = "1")
        val b = ItemListBlock("b", id = "2")
        assertNull(listTypingStep(listOf(a), listOf(a, b)))
        assertNull(listTypingStep(listOf(a), listOf(b)))
        assertNull(listTypingStep(listOf(a), listOf(a.copy(isCheck = true, value = "ax"))))
        assertNull(listTypingStep(listOf(a, b), listOf(a.copy(value = "x"), b.copy(value = "y"))))
        assertNull(listTypingStep(listOf(a), listOf(a)))
        assertNotNull(listTypingStep(listOf(a, b), listOf(a, b.copy(value = "by"))))
    }

    @Test
    fun marksOnPastedLinesAreKeptOnlyForLists() {
        assertEquals("plain", listItemText("plain").value)
        assertEquals("# heading", listItemText("# heading").value)
        assertEquals("item", listItemText("- item").value)
    }

    // --- selection, find and the cursor --------------------------------------------------------------

    @Test
    fun focusAndInteractionIgnoreBlocksThatAreNotThere() {
        val editor = editorOf(Block.SeparatorBlock(), text("a"))
        editor.selection.onInteraction("unknown")
        editor.selection.focusAt(7)
        editor.selection.focusAt(-1)
        assertEquals(1, editor.blocks.previousFocusableIndex(10))
        assertEquals(0, listOf<Block>(Block.SeparatorBlock()).previousFocusableIndex(5))
    }

    @Test
    fun findRefreshesOnlyWhileOpenWithAQuery() {
        val paragraph = text("alpha beta")
        val find = NoteFind()
        find.refresh(listOf(paragraph))
        find.open()
        find.refresh(listOf(paragraph))
        find.search("beta", listOf(paragraph))
        paragraph.text.value = "gamma"
        find.refresh(listOf(paragraph))
        assertEquals(-1, find.state.value.current)
        paragraph.text.value = "beta beta"
        find.refresh(listOf(paragraph))
        assertEquals(0, find.state.value.current)
    }

    @Test
    fun cursorsFollowJoinsAndLostItems() {
        val one = ItemListBlock("ab", id = "1")
        val two = ItemListBlock("cd", id = "2")
        assertNull(caretTargetOf("b", "same", "same"))
        assertNull(caretTargetOf("b", listOf<ItemListBlock>(), "text"))
        assertNull(caretTargetOf("b", "text", 3))
        assertNull(caretTargetOf("b", listOf(one), listOf(one, one.copy(value = "x"))))
        assertNull(caretTargetOf("b", listOf(one), listOf(two)))
        assertNull(caretTargetOf("b", listOf(one), listOf(one)))
        assertNotNull(caretTargetOf("b", listOf(one, two), listOf(one.copy(value = "xab"))))
        assertNotNull(caretTargetOf("b", listOf(one, two), listOf(one.copy(value = "abcd"))))
        assertNotNull(caretTargetOf("b", listOf(one, two), listOf(two)))
        val target = caretTargetOf("b", listOf(one), listOf(one.copy(value = "abc")))
        assertEquals(3, target?.caret)
    }

    @Test
    fun convertedBlocksCarryTheirCursorOnlyWhereItCanGo() {
        val paragraph = text("ab")
        val quote = Block.QuoteBlock(MutableStateFlow("ab"))
        val bullets = list("ab")
        assertNotNull(carriedCaret(paragraph, quote, 1, null))
        assertNull(carriedCaret(bullets, bullets, 1, "unknown"))
        assertNotNull(carriedCaret(bullets, bullets, 1, bullets.items.value[0].id))
        assertNull(carriedCaret(Block.SeparatorBlock(), Block.SeparatorBlock(), 1, null))
        assertNull(carriedCaret(paragraph, quote, null, null))
        assertNull(carriedCaret(paragraph, Block.SeparatorBlock(), 1, null))
        assertNull(carriedCaret(bullets, Block.SeparatorBlock(), 1, bullets.items.value[0].id))
    }

    // --- codecs -------------------------------------------------------------------------------------

    @Test
    fun missingTextReadsAsEmpty() {
        val blocks = BlockJson.decode("""[{"blockType":"H1"},{"blockType":"QUOTE"},{"blockType":"TEXT"}]""")
        assertEquals(3, blocks.size)
    }

    @Test
    fun markdownEdgesProduceNoEmptyBlocks() {
        assertEquals(1, MarkdownBlockMapper.toBlocks("```\ncode").size)
        assertTrue(MarkdownBlockMapper.toBlocks("```\n```").isEmpty())
        assertTrue(MarkdownBlockMapper.toBlocks("<b></b>").isEmpty())
        assertTrue(MarkdownBlockMapper.toBlocks("> <b></b>").isEmpty())
        assertTrue(MarkdownBlockMapper.toBlocks("-   ").isEmpty())
    }

    @Test
    fun entitiesOutsideTextAreKept() {
        assertEquals("&#9999999;", InlineHtml.toPlainText("&#9999999;"))
        assertEquals("&#xFFFFFF;", InlineHtml.toPlainText("&#xFFFFFF;"))
    }
}
