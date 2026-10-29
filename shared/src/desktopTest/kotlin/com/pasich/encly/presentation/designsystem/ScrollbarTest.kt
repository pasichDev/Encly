package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A desktop list shows a scrollbar while its content does not fit, in the theme's colours; a short one shows none. */
@OptIn(ExperimentalTestApi::class)
class ScrollbarTest {
    private val side = 200.dp

    @Composable
    private fun Frame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        MaterialTheme {
            Box(Modifier.testTag("frame").size(side).background(Color.White), content = content)
        }
    }

    /** Whether anything but the white background is drawn in the last 12 px at the end edge. */
    private fun ComposeUiTest.scrollbarShown(): Boolean {
        val image = onNodeWithTag("frame").captureToImage().toPixelMap()
        return (image.width - EDGE until image.width).any { x ->
            (0 until image.height).any { y -> image[x, y] != Color.White }
        }
    }

    @Test
    fun aLongListShowsItsScrollbar() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberLazyListState()
                LazyColumn(state = state) { items(ROWS) { Box(Modifier.fillMaxWidth().height(40.dp)) } }
                EnclyScrollbar(state)
            }
        }
        waitForIdle()

        assertTrue(scrollbarShown())
    }

    @Test
    fun aShortListShowsNone() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberLazyListState()
                LazyColumn(state = state) { items(2) { Box(Modifier.fillMaxWidth().height(40.dp)) } }
                EnclyScrollbar(state)
            }
        }
        waitForIdle()

        assertFalse(scrollbarShown())
    }

    @Test
    fun aLongColumnShowsItsScrollbar() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberScrollState()
                Column(Modifier.verticalScroll(state)) { repeat(ROWS) { Box(Modifier.fillMaxWidth().height(40.dp)) } }
                EnclyScrollbar(state)
            }
        }
        waitForIdle()

        assertTrue(scrollbarShown())
    }

    @Test
    fun aShortColumnShowsNone() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberScrollState()
                Column(Modifier.verticalScroll(state)) { repeat(2) { Box(Modifier.fillMaxWidth().height(40.dp)) } }
                EnclyScrollbar(state)
            }
        }
        waitForIdle()

        assertFalse(scrollbarShown())
    }

    @Test
    fun aLongGridShowsItsScrollbar() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberLazyStaggeredGridState()
                LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2), state = state) {
                    items(ROWS) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
                EnclyScrollbar(state)
            }
        }
        waitForIdle()

        assertTrue(scrollbarShown())
    }

    @Test
    fun aShortGridShowsNone() = runComposeUiTest {
        setContent {
            Frame {
                val state = rememberLazyStaggeredGridState()
                LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2), state = state) {
                    items(2) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
                EnclyScrollbar(state)
            }
        }
        waitForIdle()

        assertFalse(scrollbarShown())
    }

    @Test
    fun theGridAdapterEstimatesFromTheCellsInViewAndScrollsTo() = runComposeUiTest {
        lateinit var adapter: StaggeredGridScrollbarAdapter
        lateinit var state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
        setContent {
            Frame {
                state = rememberLazyStaggeredGridState()
                adapter = StaggeredGridScrollbarAdapter(state)
                LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2), state = state) {
                    items(ROWS) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }
        waitForIdle()

        // 200 dp in view, 40 dp cells at 2 lanes: 100 cells are 50 rows of 40 dp.
        assertEquals(200.0, adapter.viewportSize)
        assertEquals(0.0, adapter.scrollOffset)
        assertEquals(ROWS / 2 * 40.0, adapter.contentSize)

        runBlocking { adapter.scrollTo(500.0) }
        waitForIdle()

        assertEquals(500.0, adapter.scrollOffset)
        assertEquals(24, state.firstVisibleItemIndex)
    }

    private companion object {
        const val ROWS = 100
        const val EDGE = 12
    }
}
