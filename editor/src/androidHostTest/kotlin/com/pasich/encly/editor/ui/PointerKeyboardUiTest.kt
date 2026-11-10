package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.ui.sheets.BlockSheetKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a desktop gives the editor's blocks: a right click or the Menu key opens the block's actions, and
 * Ctrl / Cmd+Shift+Up and Down move it. A finger does none of it: a long press stays the field's own.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PointerKeyboardUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<Pair<BlockSheetKind, Block>>()
    private lateinit var state: BlockEditorState

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun show(vararg blocks: Block, locked: Boolean = false) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        val controller = StateController(state)
        rule.setContent { EditorHarness(controller, { kind, block -> opened += kind to block }, locked) }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun separator() = rule.onNode(hasContentDescription("Separator") and hasClickAction())

    private fun textsOf() = state.blocks.map { (it as? Block.TextBlock)?.text?.value ?: it::class.simpleName.orEmpty() }

    // --- the context menu ---------------------------------------------------------------------

    @Test
    fun aRightClickOnABlockOpensItsActionsAndPickingOneRunsIt() {
        show(text("a"), Block.SeparatorBlock(), text("c"))

        separator().performMouseInput { rightClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Move block up").assertExists()
        rule.onNodeWithText("Move block down").assertExists()
        rule.onNodeWithText("Delete block").assertExists()
        rule.onNodeWithText("Duplicate block").performClick()
        rule.waitForIdle()

        assertEquals(4, state.blocks.size)
        rule.onNodeWithText("Duplicate block").assertDoesNotExist()
    }

    @Test
    fun escapeClosesTheMenuWithoutDoingAnything() {
        show(text("a"), Block.SeparatorBlock())
        separator().performMouseInput { rightClick() }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").requestFocus()
        rule.onNodeWithText("Duplicate block").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").assertDoesNotExist()
        assertEquals(2, state.blocks.size)
    }

    @Test
    fun theFirstBlockCannotMoveUpNorTheLastDown() {
        show(Block.SeparatorBlock(), text("b"))

        separator().performMouseInput { rightClick() }
        rule.waitForIdle()

        rule.onNodeWithText("Move block up").assertDoesNotExist()
        rule.onNodeWithText("Move block down").assertExists()
    }

    @Test
    fun theOnlyBlockCannotBeDeleted() {
        show(Block.SeparatorBlock())

        separator().performMouseInput { rightClick() }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").assertExists()
        rule.onNodeWithText("Delete block").assertDoesNotExist()
    }

    @Test
    fun aChecklistOffersToLockIt() {
        val checks = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("x"))), BlockType.LIST_CHECK)
        show(text("above"), checks)
        fields()[1].performClick()

        fields()[1].performKeyInput { pressKey(Key.Menu) }
        rule.waitForIdle()
        rule.onNodeWithText("Lock checklist").performClick()
        rule.waitForIdle()

        assertTrue(checks.locked.value)
    }

    @Test
    fun theMenuKeyOpensTheMenuOfTheFocusedBlock() {
        show(text("a"), text("b"))
        fields()[0].performClick()

        fields()[0].performKeyInput { pressKey(Key.Menu) }
        rule.waitForIdle()
        rule.onNodeWithText("Move block down").assertExists()
        rule.onNodeWithText("Delete block").performClick()
        rule.waitForIdle()

        assertEquals(listOf("b"), textsOf())
    }

    @Test
    fun shiftF10OpensItToo() {
        show(text("a"), text("b"))
        fields()[0].performClick()

        fields()[0].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F10) } }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").assertExists()
    }

    @Test
    fun otherKeysDoNotOpenTheMenu() {
        show(text("a"), text("b"))
        fields()[0].performClick()

        fields()[0].performKeyInput { pressKey(Key.F10) }
        fields()[0].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F9) } }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").assertDoesNotExist()
    }

    @Test
    fun aLockedEditorOffersNoMenu() {
        show(text("a"), Block.SeparatorBlock(), locked = true)

        rule.onNode(hasContentDescription("Separator")).performMouseInput { rightClick() }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").assertDoesNotExist()
    }

    @Test
    fun aFingerOpensNoMenu() {
        show(text("a"), Block.SeparatorBlock())

        separator().performTouchInput { longClick() }
        rule.waitForIdle()

        rule.onNodeWithText("Duplicate block").assertDoesNotExist()
        assertTrue(opened.isEmpty() || opened.all { it.first == BlockSheetKind.OTHER })
    }

    // --- moving a block from the keyboard -----------------------------------------------------

    @Test
    fun controlShiftUpAndDownMoveTheFocusedBlock() {
        show(text("a"), text("b"), text("c"))
        fields()[1].performClick()

        fields()[1].performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c"), textsOf())

        fields()[0].performKeyInput {
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        }
        rule.waitForIdle()
        assertEquals(listOf("a", "b", "c"), textsOf())
    }

    @Test
    fun theChordDoesNothingAtTheEndsOfTheNote() {
        show(text("a"), text("b"))
        fields()[0].performClick()

        fields()[0].performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "b"), textsOf())
    }

    @Test
    fun aLockedEditorKeepsItsBlocksInPlace() {
        show(text("a"), text("b"), locked = true)

        rule.onNodeWithText("b").performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "b"), textsOf())
    }

    @Test
    fun otherChordsAreLeftToTheField() {
        show(text("a"), text("b"))
        fields()[1].performClick()

        fields()[1].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
        fields()[1].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.DirectionUp) } }
        fields()[1].performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        }
        fields()[1].performKeyInput {
            withKeyDown(Key.CtrlLeft) {
                withKeyDown(Key.ShiftLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }
            }
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "b"), textsOf())
    }
}
