package com.pasich.encly.editor.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.EditorProvider
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.FindMatch
import com.pasich.encly.editor.ui.blocks.CHECK_BOX_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixes from the reports on issue 76: what the editor shows and does around quotes, lists and Find. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorReportsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BlockEditorState
    private lateinit var highlights: FindHighlights
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun show(vararg blocks: Block, viewportHeight: Int? = null) {
        state = BlockEditorState { 0L }
        state.load(blocks.toList())
        val controller = StateController(state)
        rule.setContent {
            MaterialTheme {
                EditorProvider {
                    val registry = rememberBlockFocusRegistry(state.blocks)
                    val findState = state.find.state.collectAsState()
                    highlights = remember(findState) { FindHighlights(findState) }
                    listState = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    val host = remember(controller, registry, highlights) {
                        EditorBlocksHost(controller, registry, highlights) { _, _ -> }
                    }
                    val size = viewportHeight?.let { Modifier.height(it.dp) } ?: Modifier
                    Box(Modifier.fillMaxWidth().then(size)) {
                        LazyColumn(state = listState) {
                            editorBlocks(host, state.blocks, false, controller.editingLinks)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** Reveals [match] the way the screen does, on the composition's own clock. */
    private fun reveal(match: FindMatch): Boolean {
        var revealed: Boolean? = null
        rule.runOnUiThread { scope.launch { revealed = highlights.reveal(match) } }
        rule.waitForIdle()
        return revealed!!
    }

    private fun fields() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.boundsInRoot }

    // --- the gap around a quote ---------------------------------------------------------------

    @Test
    fun aQuoteHasTheSameGapAboveAndBelow() {
        show(text("before"), Block.QuoteBlock(MutableStateFlow("quoted")), text("after"))

        val (before, quote, after) = fields()

        assertEquals(quote.top - before.bottom, after.top - quote.bottom, 1f)
    }

    // --- what a touch counts as ---------------------------------------------------------------

    private val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun lockedChecklist() = Block.ListBlock(
        MutableStateFlow(listOf(ItemListBlock("milk"), ItemListBlock("eggs"))),
        BlockType.LIST_CHECK,
        locked = MutableStateFlow(true),
    )

    @Test
    fun aFingerGlidingOverAListDoesNotMakeItTheBlockTheToolbarActsOn() {
        val list = lockedChecklist()
        show(text("above"), list)

        rule.onAllNodesWithTag(CHECK_BOX_TAG, useUnmergedTree = true).onFirst().performTouchInput { swipeUp() }
        rule.waitForIdle()

        assertNull(state.selection.interactedBlockId.value)
    }

    @Test
    fun aTapOnAListMakesItTheBlockTheToolbarActsOn() {
        val list = lockedChecklist()
        show(text("above"), list)

        rule.onAllNodes(isCheckbox).onFirst().performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(list.id, state.selection.interactedBlockId.value)
    }

    // --- Find scrolls to the match itself -----------------------------------------------------

    @Test
    fun findScrollsAMatchFarDownATallParagraphIntoView() {
        val tall = text("start\n" + "line\n".repeat(150) + "needle")
        show(tall, viewportHeight = 300)
        rule.runOnIdle {
            state.find.open()
            state.find.search("needle", state.blocks)
        }
        rule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemScrollOffset)

        val revealed = reveal(state.find.state.value.matches.single())
        rule.waitForIdle()

        assertTrue(revealed)
        assertTrue(listState.firstVisibleItemScrollOffset > 0)
    }

    @Test
    fun findFindsNoPlaceForAMatchOfAFieldThatIsNotThere() {
        show(text("hello"))

        assertFalse(reveal(FindMatch("gone", null, 0, 1)))
        assertFalse(reveal(FindMatch(state.blocks.single().id, null, 99, 100)))
    }
}
