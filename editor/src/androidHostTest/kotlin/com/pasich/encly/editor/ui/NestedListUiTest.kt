package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.ui.blocks.CHECK_BOX_TAG
import com.pasich.encly.editor.ui.blocks.ITEM_HANDLE_TAG
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ARABIC_ITEM = "حليب"

/**
 * Nested lists on screen: where a level sits (on the side the item reads from), the numbers and boxes of each
 * level, the keys that indent and move an item, TalkBack's actions on it, and dragging it by its handle.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NestedListUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState

    private fun row(text: String, level: Int = 0, kind: BlockType? = null) =
        ItemListBlock(text, level = level, kind = kind)

    private fun list(type: BlockType, vararg rows: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(rows.toList()), type)

    private fun show(vararg blocks: Block, layout: LayoutDirection = LayoutDirection.Ltr, locked: Boolean = false) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        val controller = StateController(state)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layout) {
                EditorHarness(controller, { _, _ -> }, locked)
            }
        }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun handles() = rule.onAllNodesWithTag(ITEM_HANDLE_TAG)

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    private val items: List<ItemListBlock> get() = (state.blocks.first() as Block.ListBlock).items.value

    private fun shape() = items.joinToString(" ") { "${it.value}${it.level}" }

    private fun order() = items.map { it.value }

    /** TalkBack's actions on item [index], which sit on its handle (the field carries the block's own). */
    private fun customActions(index: Int): Map<String, () -> Boolean> {
        val actions = handles()[index].fetchSemanticsNode().config[SemanticsActions.CustomActions]
        return actions.associate { it.label to { it.action() } }
    }

    // --- where a level sits -----------------------------------------------------------------------

    @Test
    fun aNestedItemSitsInFromItsParentByOneMarkerColumnPerLevel() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c", 2)))

        val left = (0..2).map { bounds(fields()[it]).left }

        assertTrue(left[1] > left[0], "$left")
        assertTrue(left[2] > left[1], "$left")
        // The same step for each level.
        assertEquals(left[1] - left[0], left[2] - left[1], 1f)
    }

    @Test
    fun aNestedArabicItemSitsInOnTheRightEvenInALeftToRightApp() {
        show(list(BlockType.LIST_BULLET, row(ARABIC_ITEM), row(ARABIC_ITEM + "2", 1), row(ARABIC_ITEM + "3", 2)))

        val right = (0..2).map { bounds(fields()[it]).right }

        assertTrue(right[1] < right[0], "$right")
        assertTrue(right[2] < right[1], "$right")
    }

    @Test
    fun aNestedLatinItemSitsInOnTheLeftInARightToLeftApp() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b", 1)), layout = LayoutDirection.Rtl)

        assertTrue(bounds(fields()[1]).left > bounds(fields()[0]).left)
    }

    @Test
    fun aNestedItemInARightToLeftAppSitsInFromTheRight() {
        show(list(BlockType.LIST_BULLET, row(ARABIC_ITEM), row(ARABIC_ITEM + "2", 1)), layout = LayoutDirection.Rtl)

        assertTrue(bounds(fields()[1]).right < bounds(fields()[0]).right)
    }

    @Test
    fun aMixedListIndentsEachItemOnItsOwnSide() {
        show(list(BlockType.LIST_BULLET, row("a"), row(ARABIC_ITEM, 1), row("c", 1), row(ARABIC_ITEM + "2")))

        // Latin c is nested from the left; Arabic is nested from the right.
        assertTrue(bounds(fields()[2]).left > bounds(fields()[0]).left)
        assertTrue(bounds(fields()[1]).right < bounds(fields()[3]).right)
    }

    // --- numbers and boxes ------------------------------------------------------------------------

    @Test
    fun theNumbersStartAgainAtEveryLevel() {
        show(list(BlockType.LIST_NUMBER, row("a"), row("b", 1), row("c", 1), row("d"), row("e", 1)))

        // a=1, b=1, c=2, d=2, e=1: each level counts on from its own parent.
        rule.onAllNodesWithText("1.", useUnmergedTree = true).assertCountEquals(3)
        rule.onAllNodesWithText("2.", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun aNestedChecklistUnderABulletHasBoxesAndNumberedOnesHaveNumbers() {
        show(
            list(
                BlockType.LIST_BULLET,
                row("a"),
                row("b", 1, BlockType.LIST_CHECK),
                row("c", 1, BlockType.LIST_NUMBER),
            ),
        )

        rule.onAllNodesWithTag(CHECK_BOX_TAG, useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("1.", useUnmergedTree = true).assertCountEquals(1)
    }

    // --- keys -------------------------------------------------------------------------------------

    @Test
    fun tabIndentsTheItemTheCursorIsInAndShiftTabOutdentsIt() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b")))
        fields()[1].performClick()

        fields()[1].performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        assertEquals("a0 b1", shape())

        fields()[1].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        rule.waitForIdle()
        assertEquals("a0 b0", shape())

        state.undo()
        assertEquals("a0 b1", shape())
    }

    @Test
    fun tabInTheFirstItemIsTakenAndChangesNothing() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b")))
        fields()[0].performClick()

        fields()[0].performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()

        assertEquals("a0 b0", shape())
    }

    @Test
    fun altShiftArrowsMoveTheItemAndTheBlockMoveChordStillMovesTheBlock() {
        val paragraph = Block.TextBlock(MutableStateFlow("p"))
        show(list(BlockType.LIST_BULLET, row("a"), row("b"), row("c")), paragraph)
        fields()[0].performClick()

        fields()[0].performKeyInput {
            withKeyDown(Key.AltLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c"), order())

        fields()[1].performKeyInput {
            withKeyDown(Key.AltLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
        }
        rule.waitForIdle()
        assertEquals(listOf("a", "b", "c"), order())

        // Ctrl+Shift+Down moves the whole list below the paragraph, not the item.
        fields()[0].performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        }
        rule.waitForIdle()
        assertTrue(state.blocks[0] is Block.TextBlock)
        assertEquals(listOf("a", "b", "c"), (state.blocks[1] as Block.ListBlock).items.value.map { it.value })
    }

    @Test
    fun backspaceAtTheStartOfANestedItemBringsItOut() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b", 1)))
        fields()[1].performClick()
        fields()[1].performTextInputSelection(TextRange(0))

        fields()[1].performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()

        assertEquals("a0 b0", shape())
    }

    // --- TalkBack ---------------------------------------------------------------------------------

    @Test
    fun anItemOffersTheActionsThatApplyToIt() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c", 1)))

        // a is the only item at the top: its children are not its siblings.
        assertEquals(emptySet(), customActions(0).keys)
        assertEquals(setOf("Move item down", "Outdent item"), customActions(1).keys)
        // c can also go under b.
        assertEquals(setOf("Move item up", "Indent item", "Outdent item"), customActions(2).keys)
    }

    @Test
    fun anItemThatCanGoDeeperOffersToIndent() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b")))

        assertEquals(setOf("Move item up", "Indent item"), customActions(1).keys)
    }

    @Test
    fun theActionsMoveAndIndentTheItem() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b"), row("c")))

        rule.runOnIdle { assertTrue(customActions(0).getValue("Move item down")()) }
        assertEquals(listOf("b", "a", "c"), order())

        rule.runOnIdle { assertTrue(customActions(2).getValue("Indent item")()) }
        rule.waitForIdle()
        assertEquals("b0 a0 c1", shape())

        rule.runOnIdle { assertTrue(customActions(2).getValue("Outdent item")()) }
        rule.waitForIdle()
        assertEquals("b0 a0 c0", shape())
    }

    @Test
    fun aLockedEditorHasNoHandles() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b")), locked = true)

        handles().assertCountEquals(0)
    }

    // --- the handle -------------------------------------------------------------------------------

    @Test
    fun everyItemOfAListWithSeveralHasAHandleAndALoneItemHasNone() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b"), row("c")))
        handles().assertCountEquals(3)
        rule.onAllNodesWithTag(ITEM_HANDLE_TAG).onFirst().assertContentDescriptionEquals("Reorder item")
    }

    @Test
    fun aListOfOneItemHasNoHandle() {
        show(list(BlockType.LIST_BULLET, row("a")))

        handles().assertCountEquals(0)
    }

    @Test
    fun aLockedChecklistHasNoHandles() {
        val checklist = list(BlockType.LIST_CHECK, row("a"), row("b"))
        checklist.locked.value = true
        show(checklist)

        handles().assertCountEquals(0)
    }

    @Test
    fun draggingAnItemByItsHandleWithAMouseMovesItAsOneUndoStep() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b"), row("c")))
        val row = bounds(fields()[0]).height

        handles()[0].performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, row * 0.5f))
            moveBy(Offset(0f, row * 0.9f))
        }
        // Part way, the line shows where it would land.
        rule.waitForIdle()
        assertEquals(listOf("a", "b", "c"), order())
        handles()[0].performMouseInput { release() }
        rule.waitForIdle()

        assertEquals(listOf("b", "a", "c"), order())
        state.undo()
        assertEquals(listOf("a", "b", "c"), order())
        assertTrue(!state.canUndo.value)
    }

    @Test
    fun draggingAnItemUpMovesItAboveItsSibling() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b"), row("c")))
        val row = bounds(fields()[0]).height

        handles()[2].performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, -row * 0.5f))
            moveBy(Offset(0f, -row * 0.9f))
            release()
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "c", "b"), order())
    }

    @Test
    fun draggingAParentTakesItsChildrenAlong() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c"), row("d", 1)))
        val row = bounds(fields()[0]).height

        handles()[0].performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, row * 1.5f))
            moveBy(Offset(0f, row * 1.2f))
            release()
        }
        rule.waitForIdle()

        assertEquals("c0 d1 a0 b1", shape())
    }

    @Test
    fun aMouseOnTheHandleThatDoesNotMoveChangesNothing() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b")))

        handles()[0].performMouseInput {
            moveTo(center)
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "b"), order())
        assertTrue(!state.canUndo.value)
    }

    @Test
    fun aFingerNeedsALongPressBeforeItDragsTheItem() {
        show(list(BlockType.LIST_BULLET, row("a"), row("b"), row("c")))
        val row = bounds(fields()[0]).height

        // A quick drag is a scroll, not a move.
        handles()[0].performTouchInput {
            down(center)
            moveBy(Offset(0f, row * 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("a", "b", "c"), order())

        handles()[0].performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        handles()[0].performTouchInput {
            moveBy(Offset(0f, row * 0.6f))
            moveBy(Offset(0f, row * 0.9f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c"), order())
    }
}
