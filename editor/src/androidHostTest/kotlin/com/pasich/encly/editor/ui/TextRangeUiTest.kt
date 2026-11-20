package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.TextPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Text selected across blocks: made with the mouse, with Shift and a click or a key, and with the handles; drawn
 * behind the text; and acted on by the keys, typing and the bar.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TextRangeUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState
    private lateinit var controller: StateController
    private val calls = mutableListOf<String>()

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun show(vararg blocks: Block, canEdit: Boolean = true) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        controller = StateController(state)
        val actions = SelectedBlocksActions(
            canEdit = canEdit,
            onCopy = { calls += "copy" },
            onCopyMarkdown = { calls += "markdown" },
            onCut = { calls += "cut" },
            onDelete = { calls += "delete" },
        )
        rule.setContent { EditorHarness(controller, { _, _ -> }, bar = { TextRangeBar(state.textRange, actions) }) }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun centerOf(index: Int) = fields()[index].fetchSemanticsNode().boundsInRoot.center

    private fun point(index: Int, offset: Int) = TextPoint(state.blocks[index].id, offset)

    private fun shown() = state.blocks.map { (it as? Block.TextBlock)?.text?.value ?: it::class.simpleName.orEmpty() }

    private fun range() = state.textRange

    /** Focus [index] first (focus ends a selection), then select from [from] to [to]. */
    private fun selectAcross(index: Int, from: TextPoint, to: TextPoint) {
        fields()[index].performClick()
        rule.waitForIdle()
        rule.runOnIdle { range().select(from, to) }
        rule.waitForIdle()
    }

    private fun setSelection(index: Int, start: Int, end: Int) {
        fields()[index].performSemanticsAction(SemanticsActions.SetSelection) { it(start, end, true) }
        rule.waitForIdle()
    }

    // --- the mouse -------------------------------------------------------------------------------------

    @Test
    fun draggingTheMouseFromOneFieldIntoAnotherSelectsAcrossThem() {
        show(text("first line"), text("second line"), text("third"))
        val from = centerOf(0)
        val to = centerOf(1)

        rule.onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(Offset((from.x + to.x) / 2, (from.y + to.y) / 2))
            moveTo(to)
            release()
        }
        rule.waitForIdle()

        assertTrue(range().isCrossField)
        assertEquals(2, range().blockCount())
        assertEquals(state.blocks[0].id, range().ends.value!!.anchor.blockId)
        assertEquals(state.blocks[1].id, range().ends.value!!.head.blockId)
        // The tint is drawn behind the text of both fields.
        rule.onRoot().captureToImage()
    }

    @Test
    fun draggingBackIntoTheFirstFieldEndsTheSelectionAcrossThem() {
        show(text("first line"), text("second line"))
        val from = centerOf(0)

        rule.onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(centerOf(1))
            moveTo(from + Offset(40f, 0f))
            release()
        }
        rule.waitForIdle()

        assertFalse(range().isCrossField)
    }

    @Test
    fun aDragInsideOneFieldStaysTheFieldsOwn() {
        show(text("first line"), text("second line"))
        val from = centerOf(0)

        rule.onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(from + Offset(60f, 0f))
            release()
        }
        rule.waitForIdle()

        assertFalse(range().isCrossField)
    }

    @Test
    fun aTouchDoesNotStartOne() {
        show(text("first line"), text("second line"))

        rule.onRoot().performTouchInput {
            down(centerOf(0))
            moveTo(centerOf(1))
            up()
        }
        rule.waitForIdle()

        assertFalse(range().isCrossField)
    }

    @Test
    fun shiftClickInAnotherFieldExtendsTheFieldsSelectionIntoIt() {
        show(text("first line"), text("second line"))
        fields()[0].performClick()
        setSelection(0, 2, 2)

        rule.onAllNodes(isRoot()).onFirst().performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }
            mouse { click(centerOf(1)) }
            key { keyUp(Key.ShiftLeft) }
        }
        rule.waitForIdle()

        assertTrue(range().isCrossField)
        assertEquals(point(0, 2), range().ends.value!!.anchor)
    }

    @Test
    fun shiftClickKeepsTheAnchorOfASelectionThatExists() {
        show(text("first line"), text("second line"), text("third"))
        selectAcross(0, point(0, 3), point(1, 2))

        rule.onAllNodes(isRoot()).onFirst().performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }
            mouse { click(centerOf(2)) }
            key { keyUp(Key.ShiftLeft) }
        }
        rule.waitForIdle()

        assertEquals(point(0, 3), range().ends.value!!.anchor)
        assertEquals(state.blocks[2].id, range().ends.value!!.head.blockId)
    }

    @Test
    fun shiftClickInTheSameFieldIsTheFieldsOwn() {
        show(text("first line"), text("second line"))
        fields()[0].performClick()

        rule.onAllNodes(isRoot()).onFirst().performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }
            mouse { click(centerOf(0) + Offset(30f, 0f)) }
            key { keyUp(Key.ShiftLeft) }
        }
        rule.waitForIdle()

        assertFalse(range().isCrossField)
    }

    @Test
    fun aDragHeldAtTheTopEdgeScrollsTheListAndKeepsExtendingTheSelection() {
        show(text("first line"), text("second line"), text("third"))
        val from = centerOf(1)

        // The list scrolls frame by frame while the button is held: the clock must not run on its own.
        rule.mainClock.autoAdvance = false
        rule.onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(Offset(from.x, 4f))
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().performMouseInput { release() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertTrue(range().isCrossField)
        assertEquals(state.blocks[0].id, range().ends.value!!.head.blockId)
    }

    @Test
    fun aDragHeldAtTheBottomEdgeScrollsDown() {
        show(text("first line"), text("second line"))
        val from = centerOf(0)

        rule.mainClock.autoAdvance = false
        rule.onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(Offset(from.x, 890f * 3))
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().performMouseInput { release() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertTrue(range().isCrossField)
    }

    @Test
    fun theScrollSpeedIsZeroAwayFromTheEdges() {
        assertEquals(0f, autoScrollSpeed(500f, 1000f, 100f))
        assertTrue(autoScrollSpeed(10f, 1000f, 100f) < 0f)
        assertTrue(autoScrollSpeed(990f, 1000f, 100f) > 0f)
    }

    // --- Shift and the keys ------------------------------------------------------------------------------

    private fun shiftKey(index: Int, key: Key, ctrl: Boolean = false) = fields()[index].performKeyInput {
        withKeyDown(Key.ShiftLeft) {
            if (ctrl) withKeyDown(Key.CtrlLeft) { pressKey(key) } else pressKey(key)
        }
    }

    @Test
    fun shiftRightAtTheEndOfAFieldRunsIntoTheNextOne() {
        show(text("abc"), text("def"))
        fields()[0].performClick()
        setSelection(0, 3, 3)

        shiftKey(0, Key.DirectionRight)
        rule.waitForIdle()

        assertTrue(range().isCrossField)
        assertEquals(point(1, 0), range().ends.value!!.head)
        shiftKey(0, Key.DirectionRight)
        assertEquals(point(1, 1), range().ends.value!!.head)
    }

    @Test
    fun shiftLeftAtTheStartRunsIntoThePreviousOne() {
        show(text("abc"), text("def"))
        fields()[1].performClick()
        setSelection(1, 0, 0)

        shiftKey(1, Key.DirectionLeft)
        rule.waitForIdle()

        assertEquals(point(0, 3), range().ends.value!!.head)
    }

    @Test
    fun shiftDownFromTheLastLineAndShiftUpFromTheFirstDo() {
        show(text("abc"), text("def"), text("ghi"))
        fields()[1].performClick()
        setSelection(1, 1, 1)

        shiftKey(1, Key.DirectionDown)
        rule.waitForIdle()
        assertEquals(point(2, 1), range().ends.value!!.head)

        rule.runOnIdle { range().clear() }
        shiftKey(1, Key.DirectionUp)
        rule.waitForIdle()
        assertEquals(point(0, 1), range().ends.value!!.head)
    }

    @Test
    fun theHomeEndAndPageKeysWithShift() {
        show(text("abc"), text("def"), text("ghi"))
        fields()[1].performClick()
        setSelection(1, 1, 1)

        // Within the field Home is the field's own.
        shiftKey(1, Key.MoveHome)
        rule.waitForIdle()
        assertFalse(range().isCrossField)

        shiftKey(1, Key.PageDown)
        rule.waitForIdle()
        assertEquals(state.blocks[2].id, range().ends.value!!.head.blockId)

        shiftKey(1, Key.MoveEnd, ctrl = true)
        assertEquals(point(2, 3), range().ends.value!!.head)
        shiftKey(1, Key.MoveHome, ctrl = true)
        assertEquals(point(0, 0), range().ends.value!!.head)
        shiftKey(1, Key.PageUp)
        assertEquals(point(0, 0), range().ends.value!!.head)
    }

    @Test
    fun aShiftKeyThatStaysInTheFieldMovesTheFieldsOwnSelection() {
        show(text("abc"), text("def"))
        fields()[0].performClick()
        setSelection(0, 1, 1)

        shiftKey(0, Key.DirectionRight)
        rule.waitForIdle()

        assertFalse(range().isCrossField)
        assertTrue(range().isActive)
    }

    // --- acting on a selection ---------------------------------------------------------------------------

    @Test
    fun aFieldsOwnSelectionIsTheWholeSelectionUntilItRunsIntoAnotherField() {
        show(text("abc"), text("def"))
        fields()[0].performClick()

        setSelection(0, 1, 3)
        assertTrue(range().isActive)
        assertFalse(range().isCrossField)
        assertEquals(point(0, 1), range().ends.value!!.anchor)

        setSelection(0, 2, 2)
        assertFalse(range().isActive)
    }

    @Test
    fun deleteBackspaceAndEnterRemoveTheSelectedText() {
        show(text("alpha"), text("beta"), text("gamma"))

        for (key in listOf(Key.Delete, Key.Backspace, Key.Enter)) {
            rule.runOnIdle { state.load(listOf(text("alpha"), text("beta"), text("gamma"))) }
            rule.waitForIdle()
            selectAcross(0, point(0, 2), point(2, 2))

            fields()[0].performKeyInput { pressKey(key) }
            rule.waitForIdle()

            assertEquals(listOf("almma"), shown(), key.toString())
        }
    }

    @Test
    fun undoBringsBackWhatDeleteRemoved() {
        show(text("alpha"), text("beta"), text("gamma"))
        selectAcross(0, point(0, 2), point(2, 2))

        fields()[0].performKeyInput { pressKey(Key.Delete) }
        rule.waitForIdle()
        rule.runOnIdle { state.undo() }
        rule.waitForIdle()

        assertEquals(listOf("alpha", "beta", "gamma"), shown())
    }

    @Test
    fun escapeLeavesTheSelectionAndItsOwnKeysCostNothingWithoutOne() {
        show(text("alpha"), text("beta"))
        selectAcross(0, point(0, 1), point(1, 2))

        fields()[0].performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        assertFalse(range().isActive)

        fields()[0].performKeyInput { pressKey(Key.Delete) }
        fields()[0].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        assertEquals(listOf("alpha", "beta"), shown())
        assertTrue(controller.copies.isEmpty())
    }

    @Test
    fun copyCopyAsMarkdownAndCutGoToTheClipboard() {
        show(text("alpha"), text("beta"))
        selectAcross(0, point(0, 2), point(1, 2))

        fields()[0].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        fields()[0].performKeyInput {
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.C) } }
        }
        rule.waitForIdle()
        assertEquals(
            listOf(false to "pha\n\nbe", true to "pha\n\nbe"),
            controller.copies.take(2).map {
                it.first to
                    it.second
            },
        )

        fields()[0].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.X) } }
        rule.waitForIdle()
        assertEquals(listOf("alta"), shown())
    }

    @Test
    fun otherKeysOverASelectionAreTheFieldsOwn() {
        show(text("alpha"), text("beta"))
        selectAcross(0, point(0, 1), point(1, 2))

        fields()[0].performKeyInput { pressKey(Key.F2) }
        fields()[0].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F3) } }
        rule.waitForIdle()

        assertTrue(range().isCrossField)
    }

    @Test
    fun typingOverASelectionReplacesItInOneStep() {
        show(text("alpha"), text("beta"))
        selectAcross(0, point(0, 2), point(1, 2))

        fields()[0].performTextInput("X")
        rule.waitForIdle()

        assertEquals(listOf("alXta"), shown())
        rule.runOnIdle { state.undo() }
        rule.waitForIdle()
        assertEquals(listOf("alpha", "beta"), shown())
    }

    @Test
    fun typingIntoAListItemOverASelectionDoesTheSame() {
        val bullets = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock("two"))),
            BlockType.LIST_BULLET,
        )
        show(text("head"), bullets)
        selectAcross(0, point(0, 2), TextPoint(bullets.id, 1, bullets.items.value[1].id))

        fields()[0].performTextInput("Z")
        rule.waitForIdle()

        assertEquals("heZwo", (state.blocks[0] as Block.TextBlock).text.value)
    }

    @Test
    fun aKeyInAListItemActsOnTheSelectionToo() {
        val bullets = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock("two"))),
            BlockType.LIST_BULLET,
        )
        show(text("head"), bullets)
        fields()[1].performClick()
        setSelection(1, 3, 3)

        shiftKey(1, Key.DirectionRight)
        rule.waitForIdle()
        assertTrue(range().isCrossField)

        fields()[1].performKeyInput { pressKey(Key.Delete) }
        rule.waitForIdle()
        assertEquals(2, state.blocks.size)
        assertEquals(listOf("onetwo"), (state.blocks[1] as Block.ListBlock).items.value.map { it.value })
    }

    @Test
    fun aSelectionStartedInAListItemDraws() {
        val bullets = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock("two"))),
            BlockType.LIST_BULLET,
        )
        show(bullets, text("tail"))
        selectAcross(0, TextPoint(bullets.id, 1, bullets.items.value[0].id), point(1, 2))

        rule.onRoot().captureToImage()

        assertEquals(2, range().blockCount())
    }

    // --- the bar and the handles ----------------------------------------------------------------------------

    @Test
    fun theBarCountsTheBlocksAndItsButtonsAskTheHost() {
        show(text("alpha"), text("beta"), text("gamma"))
        selectAcross(0, point(0, 1), point(2, 1))

        rule.onNodeWithText("Blocks selected: 3").assertExists()
        for (label in listOf("Copy", "Copy as Markdown", "Cut", "Delete selected blocks")) {
            rule.onNode(hasContentDescription(label) and hasClickAction()).performClick()
        }
        assertEquals(listOf("copy", "markdown", "cut", "delete"), calls)

        rule.onNode(hasContentDescription("Clear selection") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertFalse(range().isActive)
        rule.onNodeWithText("Blocks selected: 3").assertDoesNotExist()
    }

    @Test
    fun theEndHandleDraggedIntoAnotherBlockMovesTheEndThere() {
        show(text("alpha"), text("beta"), text("gamma"))
        selectAcross(0, point(0, 1), point(1, 2))

        // The handle hangs below the cursor of "beta"; dragged on, the finger is over "gamma".
        rule.onNodeWithTag(RANGE_END_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            up()
        }
        rule.waitForIdle()

        assertEquals(point(0, 1), range().ends.value!!.anchor)
        assertEquals(state.blocks[2].id, range().ends.value!!.head.blockId)
    }

    @Test
    fun theStartHandleDraggedUpMovesTheStartAndTheEndStays() {
        show(text("alpha"), text("beta"), text("gamma"))
        selectAcross(1, point(1, 1), point(2, 2))
        val target = centerOf(0)

        val start = rule.onNodeWithTag(RANGE_START_TAG).fetchSemanticsNode().boundsInRoot.center
        rule.onNodeWithTag(RANGE_START_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, (target.y - start.y) / 2))
            moveBy(Offset(0f, (target.y - start.y) / 2))
            up()
        }
        rule.waitForIdle()

        assertEquals(state.blocks[0].id, range().orderedPoints()!!.first.blockId)
        assertEquals(state.blocks[2].id, range().orderedPoints()!!.second.blockId)
    }

    @Test
    fun aHandleDragThatIsCancelledEndsTheDrag() {
        show(text("alpha"), text("beta"), text("gamma"))
        selectAcross(0, point(0, 1), point(1, 2))

        rule.onNodeWithTag(RANGE_END_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f))
            cancel()
        }
        rule.waitForIdle()

        assertTrue(range().isActive)
    }

    @Test
    fun noSelectionHasNoHandles() {
        show(text("alpha"), text("beta"))

        rule.onNodeWithTag(RANGE_START_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RANGE_END_TAG).assertDoesNotExist()
    }
}
