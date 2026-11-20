package com.pasich.encly.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** The keyboard focus ring that every pressable thing of the editor has on a desktop. */
@OptIn(ExperimentalTestApi::class)
class PointerInteractionTest {
    private val ring = Color.Red

    @Composable
    private fun Target(enabled: Boolean) {
        CompositionLocalProvider(LocalContentColor provides ring) {
            Box(
                Modifier
                    .testTag("target")
                    .size(40.dp)
                    .background(Color.White)
                    .editorInteractive(RectangleShape, enabled)
                    .clickable(enabled = enabled) {},
            )
        }
    }

    private fun ComposeUiTest.show(enabled: Boolean = true) = setContent { Target(enabled) }

    @Test
    fun theRingFollowsAShapeThatChanges() = runComposeUiTest {
        var round by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalContentColor provides ring) {
                Box(
                    Modifier
                        .testTag("target")
                        .size(40.dp)
                        .background(Color.White)
                        .editorInteractive(if (round) CircleShape else RectangleShape)
                        .clickable {},
                )
            }
        }
        onNodeWithTag("target").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals(ring, onNodeWithTag("target").captureToImage().toPixelMap()[2, 2])

        round = true
        waitForIdle()

        assertNotEquals(ring, onNodeWithTag("target").captureToImage().toPixelMap()[2, 2])
    }

    private fun ComposeUiTest.ringPixel() = onNodeWithTag("target").captureToImage().toPixelMap()[RING_X, RING_Y]

    @Test
    fun aKeyboardFocusShowsTheRingInTheContentColour() = runComposeUiTest {
        show()
        assertEquals(Color.White, ringPixel())

        onNodeWithTag("target").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertEquals(ring, ringPixel())
    }

    @Test
    fun aMouseClickShowsNoRing() = runComposeUiTest {
        show()

        onNodeWithTag("target").performMouseInput { click() }
        waitForIdle()

        // The ripple's own hover layer tints the pixel; the ring would be the full content colour.
        assertNotEquals(ring, ringPixel())
    }

    @Test
    fun aDisabledTargetHasNoRingEvenOnTab() = runComposeUiTest {
        show(enabled = false)

        onNodeWithTag("target").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertNotEquals(ring, ringPixel())
    }

    private companion object {
        // The ring is 2 dp wide, 1 dp inside a 40 dp box; the test density is 1.
        const val RING_X = 20
        const val RING_Y = 2
    }
}
