package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.TableData
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.TextPoint
import com.pasich.encly.editor.ui.blocks.TABLE_MENU_TAG
import com.pasich.encly.editor.ui.blocks.cellFor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tables and code driven the way a user does: typing, Tab, Enter, the arrow keys and the cell menu. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TablesAndCodeUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState

    private fun table(vararg rows: List<String>, header: Boolean = true) =
        Block.TableBlock(MutableStateFlow(TableData.of(rows.toList(), header)))

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun code(value: String) = Block.CodeBlock(MutableStateFlow(value))

    private fun show(vararg blocks: Block, locked: Boolean = false) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        val controller = StateController(state)
        rule.setContent { EditorHarness(controller, { _, _ -> }, locked) }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun idle() = rule.waitForIdle()

    private fun cell(block: Block.TableBlock, row: Int, column: Int) = block.table.value.rows[row].cells[column].id

    private fun grid(block: Block.TableBlock) = block.table.value.rows.map { row -> row.cells.map { it.value } }

    private fun press(index: Int, key: Key) {
        fields()[index].performKeyInput { pressKey(key) }
        idle()
    }

    private fun menu(entry: String) {
        rule.onNodeWithTag(TABLE_MENU_TAG).performClick()
        rule.onNodeWithText(entry).performClick()
        idle()
    }

    // --- a table: typing and the cells ------------------------------------------------------------------

    @Test
    fun typingInACellChangesThatCellAndUndoPutsItBack() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        show(block)

        fields()[3].performClick()
        fields()[3].performTextInput("!")
        idle()

        assertEquals(listOf(listOf("a", "b"), listOf("c", "d!")), grid(block))
        state.undo()
        idle()
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), grid(block))
    }

    @Test
    fun aCellKeepsOneLineAndAPasteOfSeveralLinesBecomesSpaces() {
        val block = table(listOf("", ""))
        show(block)

        fields()[0].performClick()
        fields()[0].performTextInput("one\ntwo")
        idle()

        assertEquals(listOf(listOf("one two", "")), grid(block))
    }

    @Test
    fun anExternalChangeOfACellShowsInItsField() {
        val block = table(listOf("a", "b"))
        show(block)

        block.table.value = block.table.value.withCell(block.table.value.rows[0].cells[1].id, "changed")
        idle()

        rule.onNodeWithText("changed").assertExists()
    }

    // --- Tab, Enter and the arrows ----------------------------------------------------------------------

    @Test
    fun tabAndShiftTabMoveBetweenCellsAndTabAfterTheLastAddsARow() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        show(block)
        fields()[0].performClick()

        press(0, Key.Tab)
        fields()[1].assertIsFocused()
        press(1, Key.Tab)
        fields()[2].assertIsFocused()

        fields()[2].performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        idle()
        fields()[1].assertIsFocused()

        fields()[3].performClick()
        press(3, Key.Tab)
        assertEquals(3, block.table.value.rowCount)
        fields()[4].assertIsFocused()
    }

    private fun shiftTab(index: Int) {
        fields()[index].performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        idle()
    }

    @Test
    fun shiftTabInTheFirstCellStaysInTheTable() {
        show(table(listOf("a", "b")))
        fields()[0].performClick()

        shiftTab(0)

        fields()[0].assertIsFocused()
    }

    @Test
    fun shiftTabInTheFirstCellGoesToTheBlockBeforeTheTable() {
        show(text("above"), table(listOf("a", "b")))
        fields()[1].performClick()

        shiftTab(1)

        fields()[0].assertIsFocused()
    }

    @Test
    fun tabWithControlHeldIsNotTheTablesKey() {
        val block = table(listOf("a", "b"))
        show(block)
        fields()[1].performClick()

        fields()[1].performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Tab)
            keyUp(Key.CtrlLeft)
        }
        idle()

        assertEquals(1, block.table.value.rowCount)
    }

    @Test
    fun aSecondSelectAllInACellSelectsTheWholeNote() {
        show(text("above"), table(listOf("ab", "cd")))
        fields()[1].performClick()
        fields()[1].performTextInputSelection(TextRange(0, 2))

        fields()[1].performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.A)
            keyUp(Key.CtrlLeft)
        }
        idle()

        assertTrue(state.textRange.isActive)
        assertTrue(state.textRange.isCrossField)
    }

    @Test
    fun typingOverASelectionFromACellIntoTheParagraphBelowReplacesTheSelection() {
        val block = table(listOf("ab", "cd"))
        val below = text("below text")
        show(block, below)
        fields()[1].performClick()
        state.textRange.select(TextPoint(block.id, 1, cell(block, 0, 1)), TextPoint(below.id, 6))
        idle()

        fields()[1].performTextInput("x")
        idle()

        assertEquals(listOf(listOf("ab", "cd")), grid(block))
        assertEquals("xtext", (state.blocks.last() as Block.TextBlock).text.value)
    }

    @Test
    fun deletingASelectionFromAParagraphIntoACellLeavesTheTable() {
        val above = text("above")
        val block = table(listOf("ab", "cd"))
        show(above, block)
        fields()[2].performClick()
        state.textRange.select(TextPoint(above.id, 2), TextPoint(block.id, 1, cell(block, 0, 1)))
        idle()

        press(2, Key.Backspace)

        assertEquals("ab", above.text.value)
        assertEquals(2, state.blocks.size)
        assertEquals(listOf(listOf("ab", "cd")), grid(block))
    }

    @Test
    fun enterGoesDownAMoveAndAddsARowInTheLastRowWithoutChangingTheText() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        show(block)

        fields()[0].performClick()
        fields()[0].performTextInput("\n")
        idle()
        fields()[2].assertIsFocused()
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), grid(block))

        fields()[3].performClick()
        fields()[3].performTextInput("\n")
        idle()
        assertEquals(3, block.table.value.rowCount)
        fields()[5].assertIsFocused()
        assertEquals(listOf("", ""), block.table.value.rows[2].cells.map { it.value })
    }

    @Test
    fun theArrowKeysLeaveACellOnlyAtItsEdges() {
        show(table(listOf("a", "b"), listOf("c", "d")))
        fields()[0].performClick()
        fields()[0].performTextInputSelection(TextRange(1))

        press(0, Key.DirectionRight)
        fields()[1].assertIsFocused()
        fields()[1].performTextInputSelection(TextRange(0))
        press(1, Key.DirectionLeft)
        fields()[0].assertIsFocused()
        press(0, Key.DirectionDown)
        fields()[2].assertIsFocused()
        press(2, Key.DirectionUp)
        fields()[0].assertIsFocused()
    }

    @Test
    fun theArrowKeysLeaveTheTableForTheBlocksAroundIt() {
        show(text("above"), table(listOf("a", "b")), text("below"))
        fields()[1].performClick()
        fields()[1].performTextInputSelection(TextRange(0))

        press(1, Key.DirectionUp)
        fields()[0].assertIsFocused()

        fields()[2].performClick()
        fields()[2].performTextInputSelection(TextRange(1))
        press(2, Key.DirectionDown)
        fields()[3].assertIsFocused()

        // From below the cursor arrives in the table's last row, at its start.
        press(3, Key.DirectionUp)
        fields()[1].assertIsFocused()
        fields()[1].performTextInputSelection(TextRange(1))
        press(1, Key.DirectionRight)
        fields()[2].assertIsFocused()
        fields()[2].performTextInputSelection(TextRange(1))
        press(2, Key.DirectionRight)
        fields()[3].assertIsFocused()
    }

    @Test
    fun theArrowKeysDoNothingPastTheEdgesOfTheOnlyBlock() {
        show(table(listOf("a")))
        fields()[0].performClick()

        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight).forEach { press(0, it) }

        fields()[0].assertIsFocused()
    }

    // --- the cell menu ----------------------------------------------------------------------------------

    @Test
    fun theMenuAddsAndDeletesRowsAndColumnsAfterTheCellTheCursorIsIn() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        show(block)
        fields()[0].performClick()

        menu("Add row")
        assertEquals(listOf(listOf("a", "b"), listOf("", ""), listOf("c", "d")), grid(block))
        menu("Add column")
        assertEquals(listOf("a", "", "b"), grid(block)[0])
        menu("Delete column")
        assertEquals(listOf("a", "b"), grid(block)[0])
        menu("Delete row")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), grid(block))
    }

    @Test
    fun theMenuActsOnTheFirstCellWhenNoCellWasEverFocusedAndTheHeaderSwitches() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        show(block)

        menu("Add row")
        assertEquals(listOf("", ""), grid(block)[1])

        assertTrue(block.table.value.header)
        menu("Header row")
        assertTrue(!block.table.value.header)
        menu("Header row")
        assertTrue(block.table.value.header)
    }

    @Test
    fun theLastRowAndColumnCannotBeDeletedFromTheMenu() {
        val block = table(listOf("a"))
        show(block)

        menu("Delete row")
        menu("Delete column")

        assertEquals(listOf(listOf("a")), grid(block))
    }

    @Test
    fun aLockedNoteShowsNoMenuAndCannotBeTypedIn() {
        val block = table(listOf("a"))
        show(block, locked = true)

        rule.onNodeWithTag(TABLE_MENU_TAG).assertDoesNotExist()
        rule.onNodeWithText("a").performClick()
        idle()
        assertEquals(listOf(listOf("a")), grid(block))
        assertEquals(0, fields().fetchSemanticsNodes().size)
    }

    // --- focus, Find and the slash picker ----------------------------------------------------------------

    @Test
    fun theEditorFocusesTheCellItNamesOrTheLastOrTheFirst() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        show(block)

        state.selection.focusAt(0, itemId = block.table.value.rows[1].cells[0].id, caret = 1)
        idle()
        fields()[2].assertIsFocused()

        state.selection.focusAt(0, cursorToEnd = true)
        idle()
        fields()[3].assertIsFocused()

        state.selection.focusAt(0)
        idle()
        fields()[0].assertIsFocused()
    }

    @Test
    fun theRequestedCellDependsOnWhereTheCursorComesFrom() {
        val data = TableData.of(listOf(listOf("a", "b"), listOf("c", "d")))
        val a = data.rows[0].cells[0]
        val c = data.rows[1].cells[0]
        val d = data.rows[1].cells[1]

        assertEquals(c, data.cellFor(c.id, cursorToEnd = false, fromBelow = false))
        assertEquals(c, data.cellFor("gone", cursorToEnd = false, fromBelow = true))
        assertEquals(d, data.cellFor("gone", cursorToEnd = false, fromBelow = false))
        assertEquals(c, data.cellFor(null, cursorToEnd = false, fromBelow = true))
        assertEquals(d, data.cellFor(null, cursorToEnd = true, fromBelow = false))
        assertEquals(a, data.cellFor(null, cursorToEnd = false, fromBelow = false))
    }

    @Test
    fun findTintsAMatchInACell() {
        val block = table(listOf("alpha", "beta"))
        show(block)

        state.find.open()
        state.find.search("alp", state.blocks)
        idle()

        rule.onNodeWithText("alpha").assertExists()
    }

    @Test
    fun theSlashPickerMakesATable() {
        show(text(""))
        fields()[0].performClick()
        fields()[0].performTextInput("/")
        idle()
        fields()[0].performTextInput("tab")
        idle()
        rule.onNodeWithText("Table").performClick()
        idle()
        assertTrue(state.blocks.first() is Block.TableBlock)
    }

    @Test
    fun theSlashPickerMakesCode() {
        show(text(""))
        fields()[0].performClick()
        fields()[0].performTextInput("/")
        idle()
        fields()[0].performTextInput("cod")
        idle()
        rule.onNodeWithText("Code").performClick()
        idle()
        assertTrue(state.blocks.first() is Block.CodeBlock)
    }

    @Test
    fun aRightToLeftLanguageMirrorsTheColumns() {
        state = BlockEditorState { 0L }
        state.load(listOf(table(listOf("a", "b"))))
        val controller = StateController(state)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                EditorHarness(controller, { _, _ -> })
            }
        }
        idle()

        val first = fields()[0].fetchSemanticsNode().boundsInRoot.left
        val second = fields()[1].fetchSemanticsNode().boundsInRoot.left
        assertTrue(first > second, "the first column is on the right: $first, $second")
    }

    // --- code ---------------------------------------------------------------------------------------------

    @Test
    fun codeKeepsEnterSpacesAndTabsInItsOwnBlock() {
        val block = code("")
        show(block)
        rule.onNodeWithText("Code").assertExists()

        fields()[0].performClick()
        fields()[0].performTextInput("if (x) {")
        fields()[0].performTextInput("\n")
        fields()[0].performTextInput("  y()")
        press(0, Key.Tab)
        fields()[0].performTextInput("\n}")
        idle()

        assertEquals(1, state.blocks.size)
        assertEquals("if (x) {\n  y()    \n}", block.text.value)
    }

    @Test
    fun tabInCodeReplacesTheSelectionAndAWindowsLineBreakBecomesALineFeed() {
        val block = code("abc")
        show(block)
        fields()[0].performClick()
        fields()[0].performTextInputSelection(TextRange(1, 2))

        press(0, Key.Tab)
        assertEquals("a    c", block.text.value)

        fields()[0].performTextInput("\r\nz")
        idle()
        assertTrue('\r' !in block.text.value, block.text.value)
    }

    @Test
    fun aLockedCodeBlockTakesNoTab() {
        val block = code("abc")
        show(block, locked = true)

        rule.onNodeWithText("abc").performClick()
        rule.onNodeWithText("abc").performKeyInput { pressKey(Key.Tab) }
        idle()

        assertEquals("abc", block.text.value)
    }

    @Test
    fun backspaceAtTheStartOfCodeMakesItAParagraphAndAgainJoinsIt() {
        show(text("a"), code(""))

        fields()[1].performClick()
        press(1, Key.Backspace)
        assertTrue(state.blocks[1] is Block.TextBlock)

        press(1, Key.Backspace)
        assertEquals(1, state.blocks.size)
    }
}
