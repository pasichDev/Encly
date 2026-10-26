package com.pasich.encly.presentation.editor.blocks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.designsystem.markerBoxSize
import com.pasich.encly.presentation.designsystem.markerGap
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.EditNoteSettingsProvider
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where a checklist box sits against its item's text, at every line spacing and text size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ChecklistLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private var spacing by mutableStateOf(NoteLineSpacing.DEFAULT)
    private var size by mutableIntStateOf(16)
    private var metrics = LineMetrics(0f, 0f)
    private var fontDp = 0.dp

    private lateinit var block: Block.ListBlock

    private fun start(text: String) {
        block = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock(text))), BlockType.LIST_CHECK)
        val actions = Mockito.mock(BlockActions::class.java, Mockito.RETURNS_DEEP_STUBS)
        // Applies a list edit the way the editor does, so a tap on the box shows as a tick.
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            block.items.value = invocation.arguments[0] as List<ItemListBlock>
        }.`when`(actions).onListItemsChanged(Mockito.any() ?: emptyList(), Mockito.anyBoolean())
        rule.setContent {
            EnclyTheme(settings = ThemeSettings(), dark = false) {
                EditNoteSettingsProvider(baseFontSize = size.sp, lineSpacing = spacing) {
                    // The very metrics the row works from (Robolectric draws no real outlines to measure).
                    metrics = rememberLineMetrics()
                    fontDp = listFontDp()
                    ListBlock(block, actions)
                }
            }
        }
    }

    private fun show(newSpacing: NoteLineSpacing, newSize: Int) {
        spacing = newSpacing
        size = newSize
        rule.waitForIdle()
    }

    private fun boxAndText(): Pair<Rect, Rect> {
        val box = rule.onNodeWithTag(CHECK_BOX_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        return box to field
    }

    /** The field's own text layout, as the semantics report it. */
    private fun fieldLayout(): TextLayoutResult {
        val node = rule.onNode(hasSetTextAction()).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Where the first line's glyphs are centred, from the field's top, by the rule the row follows. */
    private fun glyphCentre(): Float {
        val layout = fieldLayout()
        val top = layout.getLineTop(0)
        val bottom = layout.getLineBottom(0)
        return if (bottom - top >=
            metrics.naturalHeight
        ) {
            (top + bottom) / 2f
        } else {
            layout.getLineBaseline(0) - metrics.halfCap
        }
    }

    private val spacings = NoteLineSpacing.entries
    private val sizes = listOf(12, 16, 24, 32)

    @Test
    fun theBoxIsCentredOnTheFirstLineAtEveryLineSpacingAndSize() {
        start("milk")
        for (spacing in spacings) {
            for (size in sizes) {
                show(spacing, size)
                val (box, field) = boxAndText()
                assertEquals("$spacing $size", field.top + glyphCentre(), box.center.y, 1f)
            }
        }
    }

    @Test
    fun theBoxAndTheGapFollowTheTextSize() {
        start("milk")
        val density = rule.density.density
        for (spacing in spacings) {
            for (size in sizes) {
                show(spacing, size)
                val (box, field) = boxAndText()
                val side = markerBoxSize(fontDp).value * density
                assertEquals("$spacing $size box", side, box.width, 1f)
                assertEquals("$spacing $size box", side, box.height, 1f)
                assertEquals("$spacing $size gap", markerGap(fontDp).value * density, field.left - box.right, 1f)
            }
        }
    }

    @Test
    fun aLongItemKeepsTheBoxOnItsFirstLine() {
        start("one\ntwo\nthree\nfour")
        for (spacing in spacings) {
            show(spacing, 24)
            val (box, field) = boxAndText()
            assertEquals("$spacing", true, field.height > box.height * 2)
            assertEquals("$spacing", field.top + glyphCentre(), box.center.y, 1f)
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun theBoxTogglesFromA48dpSquareAroundItAndTheTextKeepsItsOwnTaps() {
        start("milk")
        show(NoteLineSpacing.COMPACT, 14)
        val dp = rule.density.density
        val (box, field) = boxAndText()
        val centre = box.center

        // 20dp above the box's centre is outside its 28dp-tall bounds but inside a 48dp square.
        rule.onNode(isCheckbox).performTouchInput { click(Offset(centre.x, centre.y - 20 * dp)) }
        rule.waitForIdle()
        assertEquals(true, block.items.value.single().isCheck)

        // Two thirds of a box width into the text is the text's: no toggle.
        rule.onNode(isCheckbox).performTouchInput { click(Offset(field.left + 4 * dp, field.center.y)) }
        rule.waitForIdle()
        assertEquals(true, block.items.value.single().isCheck)
    }
}
