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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
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
    private var metrics = LineMetrics(0f, 0f, 0f)
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

    /** The middle of the field's first line by its real layout, where the line box holds the glyphs (not compact). */
    private fun layoutCentre(): Float {
        val layout = fieldLayout()
        return (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
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
                assertEquals("$spacing $size", field.top + metrics.centre, box.center.y, 1f)
                // The style's middle is the real layout's wherever the line box is as tall as the font.
                if (spacing != NoteLineSpacing.COMPACT) {
                    assertEquals("$spacing $size layout", field.top + layoutCentre(), box.center.y, 1f)
                }
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
            assertEquals("$spacing", field.top + metrics.centre, box.center.y, 1f)
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

    @Test
    fun anEmptyItemSitsWhereTheSameItemWillSitOnceTyped() {
        start("")
        for (spacing in spacings) {
            for (size in sizes) {
                show(spacing, size)
                val (emptyBox, emptyField) = boxAndText()
                // Where the empty line's glyphs are drawn: its layout line, lowered by the shift.
                val emptyLine = emptyField.top + layoutCentre() + metrics.emptyShift
                block.items.value = listOf(block.items.value.single().copy(value = "x"))
                rule.waitForIdle()
                val (typedBox, typedField) = boxAndText()
                assertEquals("$spacing $size box", typedBox.center.y, emptyBox.center.y, 1f)
                assertEquals("$spacing $size line", typedField.top + layoutCentre(), emptyLine, 1f)
                block.items.value = listOf(block.items.value.single().copy(value = ""))
                rule.waitForIdle()
            }
        }
    }

    @Test
    fun everyRowOfSeveralMultiLineItemsKeepsItsBoxOnItsOwnFirstLine() {
        start("a\nb\nc")
        block.items.value = List(5) { ItemListBlock("item $it\nsecond\nthird") }
        show(NoteLineSpacing.DEFAULT, 16)
        val boxes = rule.onAllNodesWithTag(CHECK_BOX_TAG, useUnmergedTree = true).fetchSemanticsNodes().map {
            it.boundsInRoot
        }
        val fields = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.boundsInRoot }
        val offsets = boxes.indices.map { boxes[it].center.y - fields[it].top }
        assertEquals(offsets.toString(), 1, offsets.map { Math.round(it) }.toSet().size)
    }

    @Test
    fun aCheckedItemIsStruckByTheFieldNotByATextDecorationAComposingWordCouldReplace() {
        start("done")
        block.items.value = listOf(block.items.value.single().copy(isCheck = true))
        show(NoteLineSpacing.DEFAULT, 16)

        val decoration = fieldLayout().layoutInput.style.textDecoration

        assertEquals(false, decoration?.contains(TextDecoration.LineThrough) == true)
    }

    @Test
    fun theStrikeRunsAcrossEveryNonEmptyLineAboveItsBaseline() {
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        val measurer = TextMeasurer(resolver, rule.density, LayoutDirection.Ltr)
        val layout = measurer.measure("first\n\nthird line", TextStyle(fontSize = 20.sp))

        val segments = strikeSegments(layout, fontSizePx = 60f)

        // The empty middle line has nothing to strike.
        assertEquals(2, segments.size)
        segments.forEachIndexed { i, s ->
            val line = if (i == 0) 0 else 2
            assertEquals(true, s.right > s.left)
            assertEquals(layout.getLineBaseline(line) - 18f, s.y, 0.5f)
        }
    }

    @Test
    fun aRightToLeftLineIsStruckAsWideAsItsText() {
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        val measurer = TextMeasurer(resolver, rule.density, LayoutDirection.Ltr)
        // A right-to-left paragraph ends on the left (Latin letters, which the test fonts have).
        val layout = measurer.measure("done item", TextStyle(fontSize = 20.sp, textDirection = TextDirection.Rtl))

        val segment = strikeSegments(layout, fontSizePx = 60f).single()

        assertEquals(layout.getLineRight(0) - layout.getLineLeft(0), segment.right - segment.left, 0.5f)
        assertEquals(true, segment.right > segment.left)
    }
}
