package com.pasich.encly.dynamicBlocks.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester


/**
 * Composable для реєстрації FocusRequester в централізованому менеджері
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
