package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Touch scrolling is the way on iOS: no scrollbar is drawn.

@Composable
actual fun BoxScope.EnclyScrollbar(state: LazyListState, modifier: Modifier) = Unit

@Composable
actual fun BoxScope.EnclyScrollbar(state: ScrollState, modifier: Modifier) = Unit

@Composable
actual fun BoxScope.EnclyScrollbar(state: LazyStaggeredGridState, modifier: Modifier) = Unit

@Composable
actual fun BoxScope.EnclyHorizontalScrollbar(state: ScrollState, modifier: Modifier) = Unit
