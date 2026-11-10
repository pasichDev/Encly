package com.pasich.encly.presentation.designsystem

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.ui.theme.EnclyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An icon-only button names itself on a long press and still works on a tap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TooltipTest {
    @get:Rule
    val rule = createComposeRule()

    private var undone = 0

    private fun show() = rule.setContent {
        EnclyTheme(settings = ThemeSettings(), dark = false) {
            EnclyToolButton(icon = EnclyIcons.Undo, contentDescription = "Undo", onClick = { undone++ })
        }
    }

    @Test
    fun aLongPressShowsTheButtonsLabel() {
        show()

        rule.onNodeWithContentDescription("Undo").performTouchInput { longClick() }
        rule.waitForIdle()

        rule.onNodeWithText("Undo").assertExists()
        assertEquals("a long press is not a tap", 0, undone)
    }

    @Test
    fun aTapStillRunsTheAction() {
        show()

        rule.onNodeWithContentDescription("Undo").performClick()

        assertEquals(1, undone)
    }
}
