package com.pasich.encly.editor.ui.blocks

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** A tap on a card's padding puts the cursor at the start (upper half) or the end (lower half) of its field. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CardTapTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = TextFieldState("hello", TextRange(2))

    private fun show() {
        val focus = FocusRequester()
        rule.setContent {
            Box(Modifier.size(200.dp).testTag("card").tapCardToFocus(state, focus)) {
                // A small field in the lower left corner, away from where the taps land.
                BasicTextField(
                    state = state,
                    modifier = Modifier.align(Alignment.BottomStart).width(8.dp).height(8.dp).focusRequester(focus),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tapAt(fractionY: Float) {
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.75f, height * fractionY)) }
        rule.waitForIdle()
    }

    @Test
    fun aTapOnTheUpperHalfPutsTheCursorAtTheStart() {
        show()

        tapAt(0.2f)

        assertEquals(TextRange(0), state.selection)
    }

    @Test
    fun aTapOnTheLowerHalfPutsTheCursorAtTheEnd() {
        show()

        tapAt(0.6f)

        assertEquals(TextRange(5), state.selection)
    }
}
