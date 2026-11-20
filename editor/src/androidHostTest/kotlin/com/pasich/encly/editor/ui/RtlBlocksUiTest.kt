package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.ui.blocks.CHECK_BOX_TAG
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ARABIC = "مرحبا بالعالم"
private const val ARABIC_ITEM = "حليب"
private const val LATIN = "hello world"

/**
 * Each paragraph reads in the direction of its own first strong character, in a left-to-right app and in a
 * right-to-left one: the text itself, and what sits around it (a list's checkbox, its number, the slash picker).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RtlBlocksUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState

    private fun show(layout: LayoutDirection, vararg blocks: Block) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        val controller = StateController(state)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layout) {
                EditorHarness(controller, { _, _ -> })
            }
        }
        rule.waitForIdle()
    }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(type: BlockType, vararg values: String) =
        Block.ListBlock(MutableStateFlow(values.map { ItemListBlock(it) }), type)

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    private fun checkboxes() = rule.onAllNodesWithTag(CHECK_BOX_TAG, useUnmergedTree = true)

    /** The direction of the first paragraph of field [index], as its text layout resolved it. */
    private fun paragraphDirection(index: Int): ResolvedTextDirection {
        val results = mutableListOf<TextLayoutResult>()
        val action = fields()[index].fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        assertTrue(action.action!!.invoke(results))
        return results.single().getParagraphDirection(0)
    }

    // --- the text ---------------------------------------------------------------------------

    @Test
    fun aLeftToRightAppReadsArabicParagraphsRightToLeftAndLatinOnesLeftToRight() {
        show(LayoutDirection.Ltr, text(ARABIC), text(LATIN))

        assertEquals(ResolvedTextDirection.Rtl, paragraphDirection(0))
        assertEquals(ResolvedTextDirection.Ltr, paragraphDirection(1))
    }

    @Test
    fun aRightToLeftAppReadsLatinParagraphsLeftToRightAndArabicOnesRightToLeft() {
        show(LayoutDirection.Rtl, text(LATIN), text(ARABIC), text("$LATIN $ARABIC"), text("$ARABIC $LATIN"))

        assertEquals(ResolvedTextDirection.Ltr, paragraphDirection(0))
        assertEquals(ResolvedTextDirection.Rtl, paragraphDirection(1))
        // Mixed text follows its first strong character.
        assertEquals(ResolvedTextDirection.Ltr, paragraphDirection(2))
        assertEquals(ResolvedTextDirection.Rtl, paragraphDirection(3))
    }

    @Test
    fun anEmptyParagraphFollowsTheAppsDirection() {
        show(LayoutDirection.Rtl, text(""), text("123 !?"))

        assertEquals(ResolvedTextDirection.Rtl, paragraphDirection(0))
        assertEquals(ResolvedTextDirection.Rtl, paragraphDirection(1))
    }

    // --- a checklist's checkboxes ----------------------------------------------------------------

    @Test
    fun anArabicChecklistItemKeepsItsCheckboxOnTheRight() {
        show(LayoutDirection.Ltr, list(BlockType.LIST_CHECK, ARABIC_ITEM))

        assertTrue(bounds(checkboxes()[0]).center.x > bounds(fields()[0]).center.x)
    }

    @Test
    fun aLatinChecklistItemKeepsItsCheckboxOnTheLeftInARightToLeftApp() {
        show(LayoutDirection.Rtl, list(BlockType.LIST_CHECK, "milk"))

        assertTrue(bounds(checkboxes()[0]).center.x < bounds(fields()[0]).center.x)
    }

    @Test
    fun eachItemOfAMixedChecklistHasItsCheckboxOnItsOwnSide() {
        show(LayoutDirection.Ltr, list(BlockType.LIST_CHECK, ARABIC_ITEM, "milk", ""))

        val sides = (0..2).map { bounds(checkboxes()[it]).center.x > bounds(fields()[it]).center.x }

        assertEquals(listOf(true, false, false), sides)
    }

    @Test
    fun anEmptyChecklistItemFollowsTheAppsDirection() {
        show(LayoutDirection.Rtl, list(BlockType.LIST_CHECK, ""))

        assertTrue(bounds(checkboxes()[0]).center.x > bounds(fields()[0]).center.x)
    }

    @Test
    fun theCheckboxFlipsToTheRightOnceArabicIsTyped() {
        show(LayoutDirection.Ltr, list(BlockType.LIST_CHECK, ""))
        assertTrue(bounds(checkboxes()[0]).center.x < bounds(fields()[0]).center.x)

        fields()[0].performClick()
        fields()[0].performTextInput(ARABIC_ITEM)
        rule.waitForIdle()

        assertTrue(bounds(checkboxes()[0]).center.x > bounds(fields()[0]).center.x)
    }

    // --- numbers and bullets ------------------------------------------------------------------------

    @Test
    fun theNumbersOfArabicItemsSitOnTheRight() {
        show(LayoutDirection.Ltr, list(BlockType.LIST_NUMBER, ARABIC_ITEM, "milk"))

        val first = bounds(rule.onNodeWithText("1.", useUnmergedTree = true))
        val second = bounds(rule.onNodeWithText("2.", useUnmergedTree = true))

        assertTrue(first.center.x > bounds(fields()[0]).center.x)
        assertTrue(second.center.x < bounds(fields()[1]).center.x)
    }

    // --- the slash picker -----------------------------------------------------------------------------

    @Test
    fun theSlashPickerOpensAtTheStartSideOfTheParagraph() {
        show(LayoutDirection.Rtl, text(""))
        fields()[0].performClick()

        fields()[0].performTextInput("/")
        rule.waitForIdle()

        val field = bounds(fields()[0])
        val picker = bounds(rule.onNodeWithText("Heading 1"))
        assertTrue(picker.right > field.center.x, "the picker hangs from the right edge: $picker in $field")
    }
}
