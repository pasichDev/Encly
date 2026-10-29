package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The scrolling column the pages use, and the scrollbar along the bottom of a row that scrolls sideways. */
@OptIn(ExperimentalTestApi::class)
class ScrollColumnTest {
    private val side = 200.dp

    @Composable
    private fun Frame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        MaterialTheme {
            Box(Modifier.testTag("frame").size(side).background(Color.White), content = content)
        }
    }

    private fun ComposeUiTest.pixelsOnTheEnd(): Boolean {
        val image = onNodeWithTag("frame").captureToImage().toPixelMap()
        return (image.width - EDGE until image.width).any { x ->
            (0 until image.height).any { y -> image[x, y] != Color.White }
        }
    }

    private fun ComposeUiTest.pixelsOnTheBottom(): Boolean {
        val image = onNodeWithTag("frame").captureToImage().toPixelMap()
        return (image.height - EDGE until image.height).any { y ->
            (0 until image.width).any { x -> image[x, y] != Color.White }
        }
    }

    @Test
    fun aLongPageShowsItsScrollbarAndScrolls() = runComposeUiTest {
        setContent {
            Frame {
                EnclyScrollColumn(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    repeat(ROWS) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }
        waitForIdle()

        assertTrue(pixelsOnTheEnd())
    }

    @Test
    fun aShortPageShowsNone() = runComposeUiTest {
        setContent {
            Frame {
                EnclyScrollColumn { repeat(2) { Box(Modifier.fillMaxWidth().height(40.dp)) } }
            }
        }
        waitForIdle()

        assertFalse(pixelsOnTheEnd())
    }

    @Test
    fun aRowWiderThanItsBoxShowsItsScrollbarAtTheBottom() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberScrollState()
                Row(Modifier.fillMaxHeight().horizontalScroll(state)) {
                    repeat(ROWS) { Box(Modifier.width(40.dp).fillMaxHeight()) }
                }
                EnclyHorizontalScrollbar(state)
            }
        }
        waitForIdle()

        assertTrue(pixelsOnTheBottom())
    }

    @Test
    fun aRowThatFitsShowsNone() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberScrollState()
                Row(Modifier.fillMaxHeight().horizontalScroll(state)) {
                    repeat(2) { Box(Modifier.width(40.dp).fillMaxHeight()) }
                }
                EnclyHorizontalScrollbar(state)
            }
        }
        waitForIdle()

        assertFalse(pixelsOnTheBottom())
    }

    @Test
    fun theContentPaddingIsInsideTheScrollingArea() = runComposeUiTest {
        setContent {
            Frame {
                EnclyScrollColumn(contentPadding = PaddingValues(top = 30.dp)) {
                    Box(Modifier.testTag("first").fillMaxWidth().height(10.dp).background(Color.Black))
                }
            }
        }
        waitForIdle()

        val top = onNodeWithTag("first").fetchSemanticsNode().boundsInRoot.top
        assertEquals(true, top > 0f)
    }

    private companion object {
        const val ROWS = 100
        const val EDGE = 12
    }
}
