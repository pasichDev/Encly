package com.pasich.encly.dynamicBlocks.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester


/**
 * Composable for registering a FocusRequester in the centralized manager.
 */
@Composable
fun RegisterFocusRequester(
    index: Int,
    focusManager: CentralizedFocusManager,
    focusRequester: FocusRequester
) {
    DisposableEffect(index, focusManager, focusRequester) {
        focusManager.registerFocusRequester(index, focusRequester)

        onDispose {
            focusManager.unregisterFocusRequester(index)
        }
    }
}
