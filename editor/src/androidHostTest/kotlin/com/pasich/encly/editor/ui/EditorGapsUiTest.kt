package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import com.pasich.encly.editor.EditorProvider
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.FocusRequest
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.ui.blocks.ListBlock
import com.pasich.encly.editor.ui.blocks.TextBlock
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keys, focus requests and odd line breaks that the scenario tests of the blocks leave out. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorGapsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState

    private fun show(vararg blocks: Block) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        val controller = StateController(state)
        rule.setContent { EditorHarness(controller, { _, _ -> }) }
        rule.waitForIdle()
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction())

    private fun press(index: Int, key: Key, selection: Int? = null) {
        // Focus by semantics, not by touch: a touch shows the cursor handle's magnifier, which Robolectric cannot draw.
        fields()[index].requestFocus()
        selection?.let { fields()[index].performTextInputSelection(TextRange(it)) }
        fields()[index].performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    @Test
    fun backspaceInAnEmptyFirstParagraphRemovesIt() {
        show(text(""), Block.SeparatorBlock())

        press(0, Key.Backspace)

        assertEquals(1, state.blocks.size)
    }

    @Test
    fun cursorKeysLeaveAParagraphFromItsEdges() {
        show(text("a"), text("b"), text("c"))

        press(0, Key.DirectionRight, selection = 1)
        press(1, Key.DirectionLeft, selection = 0)
        press(0, Key.DirectionDown, selection = 1)
        press(1, Key.DirectionUp, selection = 0)

        assertTrue(state.selection.focusedBlockId.value != null)
    }

    @Test
    fun otherKeysDuringTheSlashPickerGoToTheField() {
        show(text(""))
        fields()[0].performClick()
        fields()[0].performTextInput("/")
        rule.waitForIdle()

        fields()[0].performKeyInput { pressKey(Key.A) }
        fields()[0].performKeyInput { pressKey(Key.DirectionDown) }
        fields()[0].performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()

        assertEquals(1, state.blocks.size)
    }

    @Test
    fun cursorKeysMoveBetweenTheItemsOfAList() {
        val numbers = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock("two"), ItemListBlock("three"))),
            BlockType.LIST_NUMBER,
        )
        show(numbers)

        press(1, Key.DirectionLeft, selection = 0)
        press(0, Key.DirectionRight, selection = 3)
        press(1, Key.DirectionUp, selection = 0)
        press(0, Key.DirectionDown, selection = 3)

        assertEquals(3, numbers.items.value.size)
    }

    @Test
    fun aKeyThatDoesNothingInTheAddressFieldIsLeftToIt() {
        val block = Block.LinkBlock()
        show(text("keep"), block)
        val field = rule.onAllNodes(hasSetTextAction())[1]
        field.performClick()

        field.performKeyInput { pressKey(Key.F1) }
        rule.waitForIdle()

        assertEquals(2, state.blocks.size)
    }

    @Test
    fun backspaceInAnEmptyAddressRemovesTheLinkBlock() {
        show(text("keep"), Block.LinkBlock())
        val field = rule.onAllNodes(hasSetTextAction())[1]
        field.performClick()

        field.performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()

        assertEquals(1, state.blocks.size)
    }

    // --- a list's focus target and a field kept as text ---------------------------------------

    @Test
    fun theListFocusTargetPicksItsItemAndGivesUpOnOneThatNeverAppears() {
        val block = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("a"), ItemListBlock("b"))),
            BlockType.LIST_BULLET,
        )
        var target: ((FocusRequest) -> Unit)? = null
        val actions = Mockito.mock(BlockActions::class.java, Mockito.RETURNS_DEEP_STUBS)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            target = invocation.arguments[0] as (FocusRequest) -> Unit
            val unregister: () -> Unit = {}
            unregister
        }.`when`(actions).registerFieldsFocusTarget(Mockito.any() ?: { })
        rule.setContent { MaterialTheme { EditorProvider { ListBlock(block, actions) } } }
        rule.waitForIdle()

        rule.runOnUiThread { target!!(FocusRequest(block.id)) }
        rule.waitForIdle()
        rule.runOnUiThread { target!!(FocusRequest(block.id, cursorToEnd = true)) }
        rule.waitForIdle()
        // An item added in the same moment is composed a frame later: the request waits for it.
        rule.runOnUiThread {
            val added = ItemListBlock("late")
            block.items.value = block.items.value + added
            target!!(FocusRequest(block.id, itemId = added.id, caretX = 1f))
        }
        rule.waitForIdle()
        // An item gone before it was ever composed is given up on after a few frames.
        rule.runOnUiThread {
            val ghost = ItemListBlock("ghost")
            block.items.value = block.items.value + ghost
            target!!(FocusRequest(block.id, itemId = ghost.id))
            block.items.value = block.items.value - ghost
        }
        rule.waitForIdle()

        assertEquals(3, block.items.value.size)
    }

    private fun textWithRefusingActions(onKept: Boolean): Block.TextBlock {
        val block = Block.TextBlock()
        val actions = Mockito.mock(BlockActions::class.java, Mockito.RETURNS_DEEP_STUBS)
        Mockito.doReturn(if (onKept) "kept" else null).`when`(actions)
            .onLineBreak(Mockito.any() ?: LineBreak("", listOf("", ""), ""))
        rule.setContent { MaterialTheme { EditorProvider { TextBlock(block, actions) } } }
        rule.waitForIdle()
        return block
    }

    @Test
    fun aLineBreakTheEditorDeclinesStaysTextWithOneKindOfBreak() {
        textWithRefusingActions(onKept = false)

        fields()[0].performTextInput("a\r\nb")
        rule.waitForIdle()

        fields()[0].assertTextEquals("a\nb")
        fields()[0].performTextInput("\n")
        rule.waitForIdle()
    }

    @Test
    fun aLineBreakTheEditorKeepsReplacesTheText() {
        textWithRefusingActions(onKept = true)

        fields()[0].performTextInput("a\nb")
        rule.waitForIdle()

        fields()[0].assertTextEquals("kept")
    }
}
