package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * A visible, theme-styled scrollbar for the desktop, where a mouse wheel is not the only way to scroll and
 * a list should show how long it is. Put it in the Box that holds the scrolling content, after it, so it is
 * drawn on top at the end edge:
 *
 *     Box {
 *         LazyColumn(state = state) { ... }
 *         EnclyScrollbar(state)
 *     }
 *
 * It shows only while the content does not fit, takes drag and a click on the track, and its thumb brightens
 * under the pointer. On Android and iOS touch scrolling is the way and these draw nothing.
 */

/** The scrollbar of a [LazyListState] list (the notes list, settings, a sheet). */
@Composable
expect fun BoxScope.EnclyScrollbar(state: LazyListState, modifier: Modifier = Modifier)

/** The scrollbar of a [ScrollState] column (a dialog, a form). */
@Composable
expect fun BoxScope.EnclyScrollbar(state: ScrollState, modifier: Modifier = Modifier)

/**
 * The scrollbar of a [LazyStaggeredGridState] grid (the notes grid). A staggered grid has no known length,
 * so the thumb's size and place are estimated from the cells in view and are refined as the user scrolls.
 */
@Composable
expect fun BoxScope.EnclyScrollbar(state: LazyStaggeredGridState, modifier: Modifier = Modifier)
