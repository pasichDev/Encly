package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TableData
import com.pasich.encly.editor.state.BlockEditorState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pasting what the editor itself copied or cut, typed into the fields the way a paste arrives. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CopiedPasteUiTest {
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

    @Test
    fun aPastedCutParagraphReadsAsItWasNotAsItsMarkdown() {
        val target = Block.TextBlock()
        show(target)
        state.rememberCopied("price\\*2", listOf(Block.TextBlock(MutableStateFlow("price*2"))))

        fields()[0].performClick()
        fields()[0].performTextInput("price\\*2")
        rule.waitForIdle()

        assertEquals("price*2", (state.blocks.single() as Block.TextBlock).text.value)
    }

    @Test
    fun aPastedCutListItemTextAndTableComeBackAsTheyWere() {
        val item = ItemListBlock("")
        val list = Block.ListBlock(MutableStateFlow(listOf(item)), BlockType.LIST_BULLET)
        val target = Block.TextBlock()
        show(list, target)
        val table = Block.TableBlock(MutableStateFlow(TableData.of(listOf(listOf("a", "b|c"), listOf("1", "2")))))
        state.rememberCopied("| a | b\\|c |\n| --- | --- |\n| 1 | 2 |", listOf(table))

        fields()[0].performClick()
        fields()[0].performTextInput("| a | b\\|c |\n| --- | --- |\n| 1 | 2 |")
        rule.waitForIdle()
        // A list item takes the plain text of what was copied.
        assertEquals("a | b|c\n1 | 2", (state.blocks[0] as Block.ListBlock).items.value.joinToString("\n") { it.value })

        fields().onLast().performClick()
        fields().onLast().performTextInput("| a | b\\|c |\n| --- | --- |\n| 1 | 2 |")
        rule.waitForIdle()

        val pasted = state.blocks.filterIsInstance<Block.TableBlock>().single().table.value
        assertEquals(listOf(listOf("a", "b|c"), listOf("1", "2")), pasted.rows.map { r -> r.cells.map { it.value } })
        assertTrue(state.blocks.none { it === table })
    }
}
