package com.pasich.encly.editor.focus

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.editor.EditorLogger
import com.pasich.encly.editor.LocalEditorLogger
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.state.FocusRequest
import com.pasich.encly.editor.ui.RegisterFocusRequester
import com.pasich.encly.editor.ui.blocks.firstLineCentre
import com.pasich.encly.editor.ui.blocks.removesEmptyAddress
import com.pasich.encly.editor.ui.rememberBlockFocusRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as AndroidKeyEvent

/** The remaining directions of the focus and key rules: right-to-left text, stale layouts, changing keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class FocusBranchTest {
    @get:Rule
    val rule = createComposeRule()

    private fun fakeGeometry(lines: Int, length: Int) = object : CaretGeometry {
        override val lineCount = lines
        override val textLength = length
        override fun lineOf(offset: Int) = 0
        override fun xOf(offset: Int) = 0f
        override fun offsetAt(line: Int, x: Float) = 3
        override fun isRtl(offset: Int) = false
    }

    private fun androidKey(code: Int, action: Int = AndroidKeyEvent.ACTION_DOWN) =
        KeyEvent(AndroidKeyEvent(0L, 0L, action, code, 0, 0))

    @Test
    fun aRightToLeftParagraphIsReported() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(context), LayoutDirection.Ltr)
        val layout = measurer.measure(
            "שלום עולם",
            TextStyle(fontSize = 16.sp, textDirection = TextDirection.Rtl),
            constraints = Constraints(maxWidth = 1000),
            layoutDirection = LayoutDirection.Rtl,
        )
        assertTrue(layout.geometry().isRtl(0))
    }

    @Test
    fun aStaleOrMissingLayoutIsNotTrusted() {
        assertEquals(true, caretLine(fakeGeometry(0, 5), 5, 0).onFirst)
        assertEquals(true, caretLine(fakeGeometry(2, 9), 5, 0).onFirst)
        assertEquals(true, caretLine(fakeGeometry(2, 5), 5, 0).onFirst)
        assertEquals(5, caretOffsetFor(fakeGeometry(0, 5), 0f, fromBelow = true, length = 5))
        assertEquals(0, caretOffsetFor(fakeGeometry(0, 5), 0f, fromBelow = false, length = 5))
        assertEquals(3, caretOffsetFor(fakeGeometry(2, 5), 0f, fromBelow = false, length = 5))
    }

    @Test
    fun aCaretLineDecidesWhereArrowsLeave() {
        val handlers = KeyboardUtils.KeyHandlers(onNavigateDown = { true }, onNavigateUp = { true })
        val line = CaretLine(onFirst = false, onLast = true, x = null)
        assertTrue(KeyboardUtils.handleKey(Key.DirectionDown, KeyEventType.KeyDown, "abc", 1, handlers, line))
        assertFalse(KeyboardUtils.handleKey(Key.DirectionUp, KeyEventType.KeyDown, "abc", 1, handlers, line))
        assertTrue(KeyboardUtils.handleKey(Key.DirectionDown, KeyEventType.KeyDown, "abc", 3, handlers, null))
        assertFalse(KeyboardUtils.handleKey(Key.DirectionDown, KeyEventType.KeyDown, "abc", 1, handlers, null))
        assertFalse(KeyboardUtils.handleKey(Key.A, KeyEventType.KeyDown, "abc", 1, handlers, null))
    }

    @Test
    fun theAddressFieldGivesUpItsBlockOnlyOnBackspaceWhenEmpty() {
        assertTrue(removesEmptyAddress(androidKey(AndroidKeyEvent.KEYCODE_DEL), ""))
        assertFalse(removesEmptyAddress(androidKey(AndroidKeyEvent.KEYCODE_DEL), "x"))
        assertFalse(removesEmptyAddress(androidKey(AndroidKeyEvent.KEYCODE_DEL, AndroidKeyEvent.ACTION_UP), ""))
        assertFalse(removesEmptyAddress(androidKey(AndroidKeyEvent.KEYCODE_A), ""))
    }

    @Test
    fun theFirstLineCentreFollowsTheBoxOrTheCapitals() {
        assertEquals(5f, firstLineCentre(top = 0f, bottom = 10f, natural = 8f, baseline = 9f, capHeight = 6f), 0f)
        assertEquals(6f, firstLineCentre(top = 0f, bottom = 6f, natural = 8f, baseline = 9f, capHeight = 6f), 0f)
    }

    @Test
    fun targetsWithoutCallbacksOnlyTakeFocus() {
        val registry = BlockFocusRegistry { emptyList() }
        var focused = 0
        registry.registerFocusTarget("a") { focused++ }
        assertTrue(registry.focus(FocusRequest("a", caretX = 2f)))
        assertTrue(registry.focus(FocusRequest("a", caret = 2)))
        assertTrue(registry.focus(FocusRequest("a")))
        assertEquals(3, focused)
    }

    @Test
    fun theRegistryAndTheFieldFollowTheirKeys() {
        val blocksA = listOf<Block>(Block.TextBlock())
        val blocksB = listOf<Block>(Block.TextBlock())
        val which = mutableStateOf(blocksA)
        val id = mutableStateOf(blocksA[0].id)
        val requester = FocusRequester()
        val seen = mutableListOf<BlockFocusRegistry>()
        rule.setContent {
            CompositionLocalProvider(LocalEditorLogger provides EditorLogger.None) {
                val registry = rememberBlockFocusRegistry(which.value)
                seen += registry
                RegisterFocusRequester(id.value, registry, requester)
            }
        }
        rule.waitForIdle()
        which.value = blocksB
        id.value = blocksB[0].id
        rule.waitForIdle()

        assertTrue(seen.distinct().size >= 2)
        assertTrue(seen.last().focus(blocksB[0].id))
        assertFalse(seen.last().focus(blocksA[0].id))
    }
}
