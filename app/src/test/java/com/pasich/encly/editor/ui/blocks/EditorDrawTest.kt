package com.pasich.encly.editor.ui.blocks

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.editor.EditorProvider
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TextualBlock
import com.pasich.encly.editor.model.isEmpty
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.editor.ui.EditorBlocksHost
import com.pasich.encly.editor.ui.FindHighlights
import com.pasich.encly.editor.ui.StateController
import com.pasich.encly.editor.ui.editorBlocks
import com.pasich.encly.editor.ui.rememberBlockFocusRegistry
import com.pasich.encly.editor.ui.rememberWriteAtEnd
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the blocks draw (Find's tint, a checked item's strike), which needs real drawing, and small leftovers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorDrawTest {
    @get:Rule
    val rule = createComposeRule()

    private fun layoutOf(text: String) = ApplicationProvider.getApplicationContext<Application>().let { context ->
        TextMeasurer(createFontFamilyResolver(context), Density(context), LayoutDirection.Ltr)
            .measure(text, TextStyle(fontSize = 16.sp), constraints = Constraints(maxWidth = 400))
    }

    @Test
    fun findTintsMatchesAndACheckedItemIsStruckThrough() {
        val checks = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("done thing", isCheck = true), ItemListBlock("open thing"))),
            BlockType.LIST_CHECK,
        )
        val state = BlockEditorState { 0L }
        state.load(
            listOf(
                Block.TextBlock(MutableStateFlow("thing one thing two")),
                Block.HBlock(MutableStateFlow("thing heading"), BlockType.H1),
                Block.HBlock(MutableStateFlow("thing two"), BlockType.H2),
                Block.HBlock(MutableStateFlow("thing three"), BlockType.H3),
                Block.HBlock(MutableStateFlow("thing four"), BlockType.H4),
                Block.QuoteBlock(MutableStateFlow("thing quote")),
                checks,
            ),
        )
        val controller = StateController(state)
        rule.setContent {
            MaterialTheme {
                EditorProvider {
                    val registry = rememberBlockFocusRegistry(state.blocks)
                    val find = state.find.state.collectAsState()
                    val highlights = FindHighlights(find)
                    val host = EditorBlocksHost(controller, registry, highlights) { _, _ -> }
                    androidx.compose.foundation.lazy.LazyColumn {
                        editorBlocks(host, state.blocks, false, emptySet())
                    }
                }
            }
        }
        rule.runOnIdle {
            state.find.open()
            state.find.search("thing", state.blocks)
            state.find.next()
        }
        rule.waitForIdle()

        rule.onRoot().captureToImage()
    }

    @Test
    fun strikeSegmentsCoverEachNonEmptyLine() {
        val layout = layoutOf("some words")
        val segments = strikeSegments(layout, 16f)
        assertEquals(1, segments.size)
        assertTrue(segments.single().right > segments.single().left)
        assertTrue(strikeSegments(layoutOf(""), 16f).isEmpty())
    }

    @Test
    fun theCapitalsHeightFallsBackWhereNoOutlineExists() {
        assertTrue(capHeightPx(layoutOf("H"), 16f) > 0f)
    }

    @Test
    fun aTextBlockKnowsWhetherItIsEmpty() {
        val textual: TextualBlock = Block.TextBlock()
        assertTrue(textual.isEmpty())
        textual.text.value = "ab"
        assertTrue(!textual.isEmpty())
    }

    @Test
    fun theMailAndBlockedLinksReadTheirCards() {
        assertEquals("a@b.c" to "", parseNoteLink("mailto:a@b.c").cardLines())
        assertEquals("a@b.c", buildLinkData("mailto:a@b.c?subject=x")?.title)
        assertEquals("javascript:x" to "", parseNoteLink("javascript:x").cardLines())
        assertNull(buildLinkData("javascript:x"))
        assertTrue(isOpenableLink("https://example.com"))
    }

    @Test
    fun aHeadingOfAnotherTypeStillGetsAStyle() {
        val state = BlockEditorState { 0L }
        state.load(listOf(Block.HBlock(MutableStateFlow("odd"), BlockType.TEXT)))
        val controller = StateController(state)
        rule.setContent {
            MaterialTheme {
                EditorProvider {
                    val registry = rememberBlockFocusRegistry(state.blocks)
                    val actions = com.pasich.encly.editor.ui.BlockActionsImpl(
                        state.blocks[0],
                        controller,
                        registry,
                        FindHighlights(androidx.compose.runtime.mutableStateOf(FindState())),
                    )
                    HBlock(state.blocks[0] as Block.HBlock, actions)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun writingAtTheEndAddsAParagraph() {
        val state = BlockEditorState { 0L }
        state.load(listOf(Block.SeparatorBlock()))
        val controller = StateController(state)
        var write: (() -> Unit)? = null
        rule.setContent { Provide { write = rememberWriteAtEnd(controller) } }
        rule.waitForIdle()

        rule.runOnIdle { write!!() }

        assertEquals(2, state.blocks.size)
    }

    @Composable
    private fun Provide(content: @Composable () -> Unit) = MaterialTheme { EditorProvider(content = content) }
}
