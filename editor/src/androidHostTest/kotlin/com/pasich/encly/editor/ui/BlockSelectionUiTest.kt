package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
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
 * Selecting whole blocks: how a block is picked (a long press in its gutter, TalkBack's action, Select all pressed
 * twice, a tap while picking), what the bar over them does, the keys of a hardware keyboard, and the lock toggle
 * of a checklist.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class BlockSelectionUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState
    private lateinit var controller: StateController
    private val calls = mutableListOf<String>()

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun show(vararg blocks: Block, locked: Boolean = false, canEdit: Boolean = true) {
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
        rule.setContent {
            EditorHarness(
                controller,
                { _, _ -> },
                locked,
                bar = {
                    val ids by state.pickedBlocks.ids.collectAsState()
                    if (ids.isNotEmpty()) SelectedBlocksBar(state.pickedBlocks, actions)
                },
            )
        }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun picked() = rule.runOnIdle { state.pickedBlocks.picked().map { it.id } }

    private fun idsOf(vararg at: Int) = at.map { state.blocks[it].id }

    private fun pick(vararg at: Int) = rule.runOnIdle {
        state.pickedBlocks.select(state.blocks[at.first()].id)
        at.drop(1).forEach { state.pickedBlocks.toggle(state.blocks[it].id) }
    }.also { rule.waitForIdle() }

    private fun button(label: String) = rule.onNode(hasContentDescription(label) and hasClickAction())

    private fun chord(keys: List<Key>, then: Key) = rule.onNodeWithText("Blocks selected: ${picked().size}")
        .performKeyInput {
            keys.forEach { keyDown(it) }
            pressKey(then)
            keys.reversed().forEach { keyUp(it) }
        }

    // --- picking -------------------------------------------------------------------------------------

    @Test
    fun aLongPressInTheGutterOfABlockPicksIt() {
        show(text("a"), text("b"), text("c"))
        val y = fields()[1].fetchSemanticsNode().boundsInRoot.center.y

        rule.onRoot().performTouchInput { longClick(Offset(10f * density, y)) }
        rule.waitForIdle()

        assertEquals(idsOf(1), picked())
        rule.onNodeWithText("Blocks selected: 1").assertExists()
    }

    @Test
    fun talkBackAndTheContextMenuOfferToSelectABlock() {
        show(text("a"), Block.SeparatorBlock(), text("c"))

        rule.onNode(hasContentDescription("Separator") and hasClickAction()).performMouseInput { rightClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Select block").performClick()
        rule.waitForIdle()

        assertEquals(idsOf(1), picked())
    }

    @Test
    fun whilePickingATapPicksOrReleasesABlockAndTheLastReleaseEndsTheMode() {
        show(text("a"), text("b"), text("c"))
        pick(0)

        fields()[2].performClick()
        rule.waitForIdle()
        assertEquals(idsOf(0, 2), picked())

        fields()[0].performClick()
        fields()[2].performClick()
        rule.waitForIdle()

        assertTrue(picked().isEmpty())
        rule.onNodeWithText("Blocks selected: 1").assertDoesNotExist()
    }

    @Test
    fun aTapWhilePickingDoesNotPutTheCursorInTheText() {
        show(text("a"), text("b"))
        pick(0)

        fields()[1].performClick()
        rule.waitForIdle()

        assertEquals(null, rule.runOnIdle { state.selection.focusedBlockId.value })
    }

    @Test
    fun pickedBlocksAreMarkedSelectedAndHighlighted() {
        show(text("a"), text("b"), text("c"))
        pick(0, 1)

        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(2)
        // The highlight is drawn behind the picked blocks.
        rule.onRoot().captureToImage()
    }

    // --- Select all twice ----------------------------------------------------------------------------

    private fun selectAllKey(index: Int) = fields()[index].performKeyInput {
        withKeyDown(Key.CtrlLeft) { pressKey(Key.A) }
    }

    /** What the field's own Select all does: its whole text selected. */
    private fun selectFieldText(index: Int, length: Int) {
        fields()[index].performSemanticsAction(SemanticsActions.SetSelection) { it(0, length, true) }
        rule.waitForIdle()
    }

    @Test
    fun theFirstSelectAllSelectsTheFieldsTextAndTheSecondTheWholeNote() {
        show(text("abc"), text("def"), Block.SeparatorBlock())
        fields()[0].performClick()
        // Only part of the text is selected: the key is the field's own.
        selectFieldText(0, 1)
        fields()[0].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        rule.waitForIdle()
        assertFalse(state.textRange.isCrossField)

        selectFieldText(0, 3)
        selectAllKey(0)
        rule.waitForIdle()
        assertTrue(state.textRange.isCrossField)
        assertEquals(3, state.textRange.blockCount())
    }

    @Test
    fun anEmptyFieldSelectsTheNoteAtOnce() {
        show(text(""), text("def"))
        fields()[0].performClick()

        selectAllKey(0)
        rule.waitForIdle()

        assertEquals(2, state.textRange.blockCount())
    }

    @Test
    fun aListItemDoesTheSame() {
        val bullets = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("x"))), BlockType.LIST_BULLET)
        show(text("above"), bullets)
        fields()[1].performClick()

        selectFieldText(1, 1)
        selectAllKey(1)
        rule.waitForIdle()

        assertEquals(2, state.textRange.blockCount())
    }

    @Test
    fun whereSelectAllIsNotOfferedTheFieldKeepsTheKey() {
        show(text(""), text("def"))
        controller.offersSelectAll = false
        fields()[0].performClick()

        selectAllKey(0)
        rule.waitForIdle()

        assertFalse(state.textRange.isCrossField)
    }

    @Test
    fun otherChordsOfAAreTheFieldsOwn() {
        show(text(""), text("def"))
        fields()[0].performClick()

        fields()[0].performKeyInput { pressKey(Key.A) }
        fields()[0].performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.A) } } }
        rule.waitForIdle()

        assertFalse(state.textRange.isCrossField)
    }

    // --- the bar -------------------------------------------------------------------------------------

    @Test
    fun theBarCountsThePickedBlocksAndAnnouncesTheCountToTalkBack() {
        show(text("a"), text("b"), text("c"))
        pick(0, 1)

        rule.onNodeWithText("Blocks selected: 2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.runOnIdle { state.pickedBlocks.toggle(state.blocks[2].id) }
        rule.waitForIdle()
        rule.onNodeWithText("Blocks selected: 3").assertExists()
    }

    @Test
    fun theButtonsOfTheBarAskTheHost() {
        show(text("a"), text("b"))
        pick(0)

        button("Copy").performClick()
        button("Copy as Markdown").performClick()
        button("Cut").performClick()
        button("Delete selected blocks").performClick()

        assertEquals(listOf("copy", "markdown", "cut", "delete"), calls)
    }

    @Test
    fun cutAndDeleteAreOffAWhereTheNoteCannotChange() {
        show(text("a"), text("b"), canEdit = false)
        pick(0)

        button("Copy").assertIsEnabled()
        button("Copy as Markdown").assertIsEnabled()
        button("Cut").assertIsNotEnabled()
        button("Delete selected blocks").assertIsNotEnabled()
    }

    @Test
    fun theCloseButtonLeavesTheMode() {
        show(text("a"), text("b"))
        pick(0, 1)

        button("Clear selection").performClick()
        rule.waitForIdle()

        assertTrue(picked().isEmpty())
        rule.onNodeWithText("Blocks selected: 2").assertDoesNotExist()
    }

    @Test
    fun theKeyboardHidesAndTheBarHoldsTheKeys() {
        show(text("a"), text("b"), text("c"))
        fields()[1].performClick()
        pick(0)

        // The field that had focus has lost it: typing does not edit a picked block.
        assertEquals(null, rule.runOnIdle { state.selection.focusedBlockId.value })
    }

    // --- a hardware keyboard -------------------------------------------------------------------------

    @Test
    fun shiftArrowsExtendTheSelectionAndEscapeLeaves() {
        show(text("a"), text("b"), text("c"), text("d"))
        pick(1)

        chord(listOf(Key.ShiftLeft), then = Key.DirectionDown)
        rule.waitForIdle()
        assertEquals(idsOf(1, 2), picked())

        chord(listOf(Key.ShiftLeft), then = Key.DirectionUp)
        chord(listOf(Key.ShiftLeft), then = Key.DirectionUp)
        rule.waitForIdle()
        assertEquals(idsOf(0, 1), picked())

        rule.onNodeWithText("Blocks selected: 2").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        assertTrue(picked().isEmpty())
    }

    @Test
    fun controlAPicksEverythingAndTheClipboardKeysAskTheHost() {
        show(text("a"), text("b"), text("c"))
        pick(0)

        chord(listOf(Key.CtrlLeft), then = Key.A)
        rule.waitForIdle()
        assertEquals(3, picked().size)

        chord(listOf(Key.CtrlLeft), then = Key.C)
        chord(listOf(Key.CtrlLeft, Key.ShiftLeft), then = Key.C)
        chord(listOf(Key.MetaLeft), then = Key.X)
        rule.onNodeWithText("Blocks selected: 3").performKeyInput { pressKey(Key.Delete) }

        assertEquals(listOf("copy", "markdown", "cut", "delete"), calls)
    }

    @Test
    fun cutAndDeleteKeysDoNothingWhereTheNoteCannotChange() {
        show(text("a"), text("b"), canEdit = false)
        pick(0)

        chord(listOf(Key.CtrlLeft), then = Key.X)
        rule.onNodeWithText("Blocks selected: 1").performKeyInput { pressKey(Key.Backspace) }

        assertTrue(calls.isEmpty())
    }

    @Test
    fun otherKeysAreNotTakenByTheBar() {
        show(text("a"), text("b"))
        pick(0)

        rule.onNodeWithText("Blocks selected: 1").performKeyInput { pressKey(Key.Q) }
        rule.waitForIdle()

        assertFalse(picked().isEmpty())
        assertTrue(calls.isEmpty())
    }

    // --- the lock of a checklist -----------------------------------------------------------------------

    private fun checklist() = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("x"))), BlockType.LIST_CHECK)

    @Test
    fun aChecklistTheUserWorksInOffersALockThatLocksAndUnlocksIt() {
        val checks = checklist()
        show(text("above"), checks)
        rule.onNodeWithContentDescription("Lock checklist").assertDoesNotExist()

        fields()[1].performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Lock checklist").performClick()
        rule.waitForIdle()

        assertTrue(checks.locked.value)
        rule.onNodeWithContentDescription("Lock checklist").assertDoesNotExist()
        rule.onNodeWithContentDescription("Unlock checklist").performClick()
        rule.waitForIdle()

        assertFalse(checks.locked.value)
        rule.onNodeWithContentDescription("Lock checklist").assertExists()
    }

    @Test
    fun aLockedChecklistOffersToUnlockOnceTheUserWorksOnIt() {
        val checks = checklist().also { it.locked.value = true }
        show(text("above"), checks)
        rule.onNodeWithContentDescription("Unlock checklist").assertDoesNotExist()

        rule.runOnIdle { state.selection.onInteraction(checks.id) }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Unlock checklist").assertExists()
    }

    @Test
    fun otherListsHaveNoLockToggle() {
        val bullets = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("x"))), BlockType.LIST_BULLET)
        show(text("above"), bullets)

        fields()[1].performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Lock checklist").assertDoesNotExist()
    }

    @Test
    fun aLockedEditorOffersNoLockToggle() {
        val checks = checklist()
        show(text("above"), checks, locked = true)

        rule.runOnIdle { state.selection.onInteraction(checks.id) }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Lock checklist").assertDoesNotExist()
    }
}
