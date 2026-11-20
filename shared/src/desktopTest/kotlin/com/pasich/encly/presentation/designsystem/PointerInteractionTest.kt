package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Keyboard focus shows a ring in the content colour, and a mouse click or a disabled target does not. */
@OptIn(ExperimentalTestApi::class)
class PointerInteractionTest {
    private val ring = Color.Red

    @Composable
    private fun Target(enabled: Boolean, round: Boolean) {
        CompositionLocalProvider(LocalContentColor provides ring) {
            val base = Modifier
                .testTag("target")
                .size(40.dp)
                .background(Color.White)
            Box(
                (if (round) base.enclyInteractiveRound(enabled) else base.enclyInteractive(RectangleShape, enabled))
                    .clickable(enabled = enabled) {},
            )
        }
    }

    private fun ComposeUiTest.show(enabled: Boolean = true, round: Boolean = false) =
        setContent { Target(enabled, round) }

    private fun ComposeUiTest.pixel(x: Int, y: Int) = onNodeWithTag("target").captureToImage().toPixelMap()[x, y]

    @Test
    fun aKeyboardFocusShowsTheRingInTheContentColour() = runComposeUiTest {
        show()
        assertEquals(Color.White, pixel(RING_X, RING_Y))

        onNodeWithTag("target").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertEquals(ring, pixel(RING_X, RING_Y))
    }

    @Test
    fun aRoundTargetRingsItsCircle() = runComposeUiTest {
        show(round = true)

        onNodeWithTag("target").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertEquals(ring, pixel(RING_X, RING_Y))
        // The corner is outside the circle.
        assertNotEquals(ring, pixel(RING_Y, RING_Y))
    }

    @Test
    fun aMouseClickShowsNoRing() = runComposeUiTest {
        show()

        onNodeWithTag("target").performMouseInput { click() }
        waitForIdle()

        // The ripple's own hover layer tints the pixel; the ring would be the full content colour.
        assertNotEquals(ring, pixel(RING_X, RING_Y))
    }

    @Test
    fun aDisabledTargetHasNoRingEvenOnTab() = runComposeUiTest {
        show(enabled = false)

        onNodeWithTag("target").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertNotEquals(ring, pixel(RING_X, RING_Y))
    }

    private companion object {
        // The ring is 2 dp wide, 1 dp inside a 40 dp box; the test density is 1.
        const val RING_X = 20
        const val RING_Y = 2
    }
}
