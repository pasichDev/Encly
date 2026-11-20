package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
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
 * The editor's blocks driven the way a user does, with nothing of the app around them: a list of
 * blocks over a plain state, typing, keys, taps and TalkBack's actions.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorBlocksUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<Pair<BlockSheetKind, Block>>()
    private lateinit var state: BlockEditorState
    private lateinit var controller: StateController

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(type: BlockType, vararg values: String) =
        Block.ListBlock(MutableStateFlow(values.map { ItemListBlock(it) }), type)

    private fun link(url: String, isError: Boolean = false) =
        Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "t", url = url, isError = isError)))

    private fun show(vararg blocks: Block, locked: Boolean = false, after: (@Composable () -> Unit)? = null) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        controller = StateController(state)
        rule.setContent { EditorHarness(controller, { kind, block -> opened += kind to block }, locked, after) }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun idle() = rule.waitForIdle()

    private fun last(matcher: SemanticsMatcher) =
        rule.onAllNodes(matcher).let { all -> all[all.fetchSemanticsNodes().size - 1] }

    private fun textsOf() = state.blocks.map { (it as? Block.TextBlock)?.text?.value ?: it::class.simpleName.orEmpty() }

    // --- a paragraph --------------------------------------------------------------------------

    @Test
    fun typingGoesToTheModelAndOnlyBlockShowsItsPlaceholder() {
        show(Block.TextBlock())
        rule.onNodeWithText("Text").assertExists()

        fields()[0].performTextInput("hello")
        idle()

        assertEquals(listOf("hello"), textsOf())
    }

    @Test
    fun enterSplitsAtTheCursorAndBackspaceJoinsAgain() {
        show(text("ab"))
        fields()[0].performClick()
        fields()[0].performTextInputSelection(TextRange(1))

        fields()[0].performTextInput("\n")
        idle()
        assertEquals(listOf("a", "b"), textsOf())

        fields()[1].performTextInputSelection(TextRange(0))
        fields()[1].performKeyInput { pressKey(Key.Backspace) }
        idle()
        assertEquals(listOf("ab"), textsOf())
    }

    @Test
    fun aHashMarkMakesAHeading() = markMakes("# ") { it is Block.HBlock }

    @Test
    fun aDashMarkMakesABulletList() = markMakes("- ") { (it as? Block.ListBlock)?.blockType == BlockType.LIST_BULLET }

    @Test
    fun aBracketMarkMakesAChecklist() = markMakes("[] ") { (it as? Block.ListBlock)?.blockType == BlockType.LIST_CHECK }

    @Test
    fun aNumberMarkMakesANumberedList() = markMakes("1. ") {
        (it as? Block.ListBlock)?.blockType ==
            BlockType.LIST_NUMBER
    }

    @Test
    fun aGreaterThanMarkMakesAQuote() = markMakes("> ") { it is Block.QuoteBlock }

    @Test
    fun threeDashesMakeASeparator() = markMakes("---") { it is Block.SeparatorBlock }

    private fun markMakes(mark: String, check: (Block) -> Boolean) {
        show(text(""))
        fields()[0].performClick()
        fields()[0].performTextInput(mark)
        idle()
        assertTrue(check(state.blocks.first()), state.blocks.map { it::class.simpleName }.toString())
    }

    @Test
    fun aLoneWebAddressPastedBecomesALink() {
        show(text(""))
        fields()[0].performClick()

        fields()[0].performTextInput("https://example.com/page")
        idle()

        assertTrue(state.blocks.first() is Block.TextBlock || state.blocks.first() is Block.LinkBlock)
    }

    // --- the slash picker ---------------------------------------------------------------------

    @Test
    fun theSlashPickerOffersTheBlockKindsAndAPickTurnsTheParagraph() {
        show(text(""))
        fields()[0].performClick()

        fields()[0].performTextInput("/")
        idle()
        rule.onNodeWithText("Heading 1").assertExists()
        rule.onNodeWithText("Heading 1").performClick()
        idle()

        assertTrue(state.blocks.first() is Block.HBlock)
    }

    @Test
    fun aQueryNarrowsTheSlashPicker() {
        show(text(""))
        fields()[0].performClick()

        fields()[0].performTextInput("/")
        idle()
        fields()[0].performTextInput("quo")
        idle()

        rule.onNodeWithText("Quote").assertExists()
        fields()[0].performKeyInput { pressKey(Key.Escape) }
        idle()
    }

    // --- lists --------------------------------------------------------------------------------

    @Test
    fun typingAndEnterInAListWorkOnItsItems() {
        val bullets = list(BlockType.LIST_BULLET, "a")
        show(bullets)
        fields()[0].performClick()
        fields()[0].performTextInputSelection(TextRange(1))

        fields()[0].performTextInput("b")
        fields()[0].performTextInput("\n")
        idle()

        assertEquals(listOf("ab", ""), bullets.items.value.map { it.value })
    }

    @Test
    fun backspaceAtTheStartOfAnItemJoinsItToTheOneAbove() {
        val bullets = list(BlockType.LIST_BULLET, "a", "b")
        show(bullets)
        fields()[1].performClick()
        fields()[1].performTextInputSelection(TextRange(0))

        fields()[1].performKeyInput { pressKey(Key.Backspace) }
        idle()

        assertEquals(listOf("ab"), bullets.items.value.map { it.value })
    }

    @Test
    fun tickingAChecklistBoxChecksTheItem() {
        val checks = list(BlockType.LIST_CHECK, "milk")
        show(checks)

        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).performClick()
        idle()

        assertTrue(checks.items.value.single().isCheck)
    }

    @Test
    fun arrowKeysMoveBetweenItemsAndBlocks() {
        show(list(BlockType.LIST_NUMBER, "one", "two"), text("below"))
        fields()[0].performClick()

        fields()[0].performKeyInput { pressKey(Key.DirectionDown) }
        idle()
        fields()[1].performKeyInput { pressKey(Key.DirectionDown) }
        idle()
        fields()[2].performKeyInput { pressKey(Key.DirectionUp) }
        idle()

        assertTrue(state.selection.focusedBlockId.value != null)
    }

    // --- links --------------------------------------------------------------------------------

    @Test
    fun anAddressIsEnteredAndSavedAsALink() {
        val block = Block.LinkBlock()
        show(block)

        rule.onNode(hasSetTextAction()).performTextInput("example.com")
        rule.onNode(
            hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).performClick()
        idle()

        assertEquals("https://example.com", block.block.value.url)
    }

    @Test
    fun anAddressThatMayNotBeSavedIsRejectedWithAMessage() {
        val block = Block.LinkBlock()
        show(block)

        rule.onNode(hasSetTextAction()).performTextInput("javascript:alert(1)")
        rule.onNode(
            hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).performClick()
        idle()

        assertEquals("", block.block.value.url)
        rule.onNodeWithText("Only web (http, https) and email links can be saved or opened.").assertExists()
    }

    @Test
    fun aSavedLinkShowsAsACardThatOpensItsSheet() {
        show(link("https://www.example.com/path?q=1"))

        rule.onNodeWithText("example.com", substring = true).assertExists()
        rule.onAllNodes(hasClickAction())[0].performClick()
        idle()

        assertEquals(BlockSheetKind.LINK, opened.single().first)
    }

    @Test
    fun aLinkThatCannotBeOpenedSaysSoAndOneThatFailedToLoadToo() {
        show(link("javascript:alert(1)"), link("https://example.com", isError = true))

        rule.onNodeWithText("Only web (http, https) and email links can be saved or opened.").assertExists()
        rule.onNodeWithText("Couldn't load the content").assertExists()
    }

    @Test
    fun editingASavedLinkReopensItsAddressForEntry() {
        val saved = link("https://example.com")
        show(saved)
        rule.runOnIdle { controller.editLink(saved) }
        idle()

        rule.onNode(hasSetTextAction()).assertExists()
        rule.onNode(hasSetTextAction()).performTextReplacement("https://example.com/more")
        rule.onNode(
            hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).performClick()
        idle()

        assertEquals("https://example.com/more", saved.block.value.url)
    }

    // --- TalkBack, the page and find ----------------------------------------------------------

    private fun customAction(label: String) {
        val node = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNodes()
            .first { node -> node.config[SemanticsActions.CustomActions].any { it.label == label } }
        rule.runOnUiThread { node.config[SemanticsActions.CustomActions].first { it.label == label }.action() }
        idle()
    }

    @Test
    fun talkBackCanMoveDuplicateAndDeleteABlock() {
        show(text("a"), text("b"), text("c"))
        fields()[1].performClick()

        customAction("Move block up")
        assertEquals(listOf("b", "a", "c"), textsOf())
        customAction("Move block down")
        assertEquals(listOf("a", "b", "c"), textsOf())
        customAction("Duplicate block")
        assertEquals(4, state.blocks.size)
        customAction("Delete block")
        assertEquals(3, state.blocks.size)
    }

    @Test
    fun talkBackCanLockAChecklist() {
        val checks = list(BlockType.LIST_CHECK, "x")
        show(text("above"), checks)

        customAction("Lock checklist")

        assertTrue(checks.locked.value)
        customAction("Unlock checklist")
        assertTrue(!checks.locked.value)
    }

    @Test
    fun findTintsTheMatchesWithoutTouchingTheText() {
        val bullets = list(BlockType.LIST_BULLET, "hello item")
        show(text("hello world hello"), Block.HBlock(MutableStateFlow("hello title"), BlockType.H1), bullets)

        rule.runOnIdle {
            state.find.open()
            state.find.search("hello", state.blocks)
            state.find.next()
        }
        idle()

        assertEquals("hello world hello", (state.blocks[0] as Block.TextBlock).text.value)
        rule.onNodeWithText("hello world hello").assertExists()
    }

    @Test
    fun aSeparatorOpensItsSheetUnlessTheEditorIsLocked() {
        show(text("a"), Block.SeparatorBlock())
        rule.onNode(hasContentDescription("Separator") and hasClickAction()).performClick()
        idle()
        assertEquals(BlockSheetKind.OTHER, opened.single().first)
    }

    @Test
    fun aLockedEditorOffersNoSheetAndNoPlaceholder() {
        show(Block.SeparatorBlock(), text(""), locked = true)

        rule.onAllNodes(hasClickAction())[0].performClick()
        idle()

        assertTrue(opened.isEmpty())
    }

    @Test
    fun theAreaBelowTheBlocksAddsAParagraphAfterANonParagraph() {
        show(Block.SeparatorBlock(), after = { Text("after-marker") })

        rule.onNode(hasText("after-marker")).assertExists()
        last(hasClickAction()).performClick()
        idle()

        assertEquals(2, state.blocks.size)
    }
}
