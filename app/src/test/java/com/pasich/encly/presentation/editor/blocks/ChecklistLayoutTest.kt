package com.pasich.encly.presentation.editor.blocks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.sp
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
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
    private var halfCap = 0f

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
                    // The very value the row aligns by (Robolectric draws no real outlines to measure).
                    halfCap = rememberHalfCapHeight()
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

    private fun boxAndText(): Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect> {
        val box = rule.onNode(isCheckbox).fetchSemanticsNode().boundsInRoot
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        return box to field
    }

    private fun firstBaseline(): Float {
        val node = rule.onNode(hasSetTextAction()).fetchSemanticsNode()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return node.boundsInRoot.top + results.single().getLineBaseline(0)
    }

    @Test
    fun theBoxIsCentredOnTheFirstLinesCapitalsAtEveryLineSpacingAndSize() {
        start("milk")
        for (spacing in NoteLineSpacing.entries) {
            for (size in listOf(14, 24)) {
                show(spacing, size)
                val (box, _) = boxAndText()
                assertEquals("$spacing $size", firstBaseline() - halfCap, box.center.y, 1f)
            }
        }
    }

    @Test
    fun theGapToTheTextIsTheMarkerColumnAtEverySpacingAndSize() {
        val widths = mutableSetOf<Float>()
        start("milk")
        for (spacing in NoteLineSpacing.entries) {
            for (size in listOf(14, 24)) {
                show(spacing, size)
                val (box, field) = boxAndText()
                widths += field.left - box.left
            }
        }
        assertEquals(widths.toString(), 1, widths.size)
    }

    @Test
    fun aLongItemKeepsTheBoxOnItsFirstLine() {
        val long = "one\ntwo\nthree\nfour"
        start(long)
        show(NoteLineSpacing.COMPACT, 24)
        val (box, field) = boxAndText()
        assertEquals("$box $field", true, field.height > box.height * 2)
        // The box column is one line tall and starts where the text field's padding does.
        assertEquals(6f, field.top - box.top, 1f)
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
