package com.pasich.encly.editor.focus

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
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
import com.pasich.encly.editor.ui.rememberBlockFocusRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import android.view.KeyEvent as AndroidKeyEvent

/** Focus handling that needs Compose types (layouts, key events, composition) rather than a screen. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class FocusGapsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun key(code: Int, action: Int = AndroidKeyEvent.ACTION_DOWN, meta: Int = 0) =
        KeyEvent(AndroidKeyEvent(0L, 0L, action, code, 0, meta))

    // --- keys --------------------------------------------------------------------------------

    @Test
    fun enterRunsItsHandlerOnKeyDownOnly() {
        var entered = 0
        val handled = KeyboardUtils.handleKeyEvent(key(AndroidKeyEvent.KEYCODE_ENTER), onEnterPressed = {
            entered++ > -1
        })
        val ignored = KeyboardUtils.handleKeyEvent(
            key(AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.ACTION_UP),
            onEnterPressed = { entered++ > -1 },
        )

        assertTrue(handled)
        assertFalse(ignored)
        assertEquals(1, entered)
    }

    @Test
    fun anArrowWithAModifierNeverLeavesTheField() {
        val shiftDown = key(AndroidKeyEvent.KEYCODE_DPAD_DOWN, meta = AndroidKeyEvent.META_SHIFT_ON)
        assertFalse(KeyboardUtils.handleKeyEvent(shiftDown, onNavigateDown = { true }))
        for (meta in listOf(AndroidKeyEvent.META_CTRL_ON, AndroidKeyEvent.META_ALT_ON, AndroidKeyEvent.META_META_ON)) {
            assertFalse(
                KeyboardUtils.handleKeyEvent(key(AndroidKeyEvent.KEYCODE_DPAD_UP, meta = meta), onNavigateUp = {
                    true
                }),
            )
        }
        assertTrue(KeyboardUtils.handleKeyEvent(key(AndroidKeyEvent.KEYCODE_DPAD_DOWN), onNavigateDown = { true }))
    }

    // --- layouts -----------------------------------------------------------------------------

    @Test
    fun aMeasuredTextAnswersTheCaretQuestions() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(context), LayoutDirection.Ltr)
        val layout = measurer.measure(
            "alpha beta gamma delta",
            TextStyle(fontSize = 16.sp),
            constraints = Constraints(maxWidth = 100),
        )
        val geometry = layout.geometry()

        assertTrue(geometry.lineCount >= 1)
        assertEquals(22, geometry.textLength)
        assertEquals(0, geometry.lineOf(0))
        assertTrue(geometry.lineOf(21) >= 0)
        assertTrue(geometry.xOf(0) >= 0f)
        assertFalse(geometry.isRtl(0))
        assertFalse(geometry.isRtl(500))
        assertTrue(geometry.offsetAt(0, 1_000f) in 0..22)
        assertNotNull(caretLine(geometry, 22, 5))
        assertEquals(true, caretLine(geometry, 22, 0).onFirst)
        assertNotNull(caretLine(geometry, 22, 22))
        assertTrue(caretOffsetFor(geometry, 0f, fromBelow = true, length = 22) in 0..22)
    }

    @Test
    fun withoutALayoutOnlyTheEndsOfTheTextAreTheFirstAndLastLine() {
        assertEquals(true, caretLine(null, 5, 0).onFirst)
        assertEquals(false, caretLine(null, 5, 2).onFirst)
        assertEquals(5, caretOffsetFor(null, 0f, fromBelow = true, length = 5))
        assertEquals(0, caretOffsetFor(null, 0f, fromBelow = false, length = 5))
        assertNull(caretLine(null, 5, 2).x)
    }

    // --- the registry ------------------------------------------------------------------------

    @Test
    fun aTargetThatNeverAppearsIsReportedOnceAndAFailingOneIsSurvived() = runTest {
        val warnings = mutableListOf<String>()
        val blocks = listOf<Block>(Block.TextBlock(), Block.TextBlock())
        val registry = BlockFocusRegistry(EditorLogger { _, event -> warnings += event }) { blocks }

        registry.focusWhenComposed(FocusRequest("nowhere"))
        assertEquals(listOf("Focus target never appeared"), warnings)

        registry.registerFocusTarget(blocks[0].id) { throw IllegalStateException("not attached") }
        assertTrue(registry.focus(blocks[0].id))
        assertTrue(warnings.last().startsWith("Focus request failed"))

        var scrolled = ""
        registry.focusWhenComposed(FocusRequest(blocks[0].id)) { scrolled = it }
        assertEquals("", scrolled)
    }

    @Test
    fun theNeighboursOfABlockThatIsNotThereAreNone() {
        val blocks = listOf<Block>(Block.TextBlock(), Block.TextBlock())
        val registry = BlockFocusRegistry { blocks }

        assertNull(registry.nextFocusableBlock("gone"))
        assertNull(registry.previousFocusableBlock("gone"))
        assertNull(registry.previousFocusableBlock(blocks[0].id))
        assertNull(registry.nextFocusableBlock(blocks[1].id))
    }

    @Test
    fun theRegistryFollowsTheCompositionAndUnregistersWithIt() {
        val blocks = mutableStateListOf<Block>(Block.TextBlock())
        val requester = FocusRequester()
        var registry: BlockFocusRegistry? = null
        val show = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalEditorLogger provides EditorLogger.None) {
                registry = rememberBlockFocusRegistry(blocks)
                if (show.value) RegisterFocusRequester(blocks[0].id, registry!!, requester)
            }
        }
        rule.waitForIdle()
        assertTrue(registry!!.focus(blocks[0].id)) // registered while composed

        show.value = false
        rule.waitForIdle()
        assertFalse(registry!!.focus(blocks[0].id)) // gone with the field
    }
}
