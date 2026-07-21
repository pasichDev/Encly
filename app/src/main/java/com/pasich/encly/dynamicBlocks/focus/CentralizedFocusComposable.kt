package com.pasich.encly.dynamicBlocks.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay



/**
 * Composable helper for handling auto-scrolling on focus change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollOnFocus(
    isFocused: Boolean,
    bringIntoViewRequester: BringIntoViewRequester,
    delay: Long = 100L
) {
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(delay)
            bringIntoViewRequester.bringIntoView()
        }
    }
}

/**
 * Modifier for adding centralized focus management.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.centralizedFocusManagement(
    index: Int = 0,
    onFocusChanged: (Int, FocusState) -> Unit = { _, _ -> },
    enableAutoScroll: Boolean = true,
    scrollDelay: Long = 100L
): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Handle auto-scrolling
    if (enableAutoScroll) {
        AutoScrollOnFocus(
            isFocused = isFocused,
            bringIntoViewRequester = bringIntoViewRequester,
            delay = scrollDelay
        )
    }

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            onFocusChanged(index, focusState)
        }
}
