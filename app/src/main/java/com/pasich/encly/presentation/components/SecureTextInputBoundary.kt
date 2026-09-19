package com.pasich.encly.presentation.components

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Requests that compliant Android input methods do not learn from text entered in Encly.
 *
 * Encly supports API 26 and above, where [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING]
 * is available. A third-party IME remains part of the device trust boundary and may ignore
 * this request.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SecureTextInputBoundary(content: @Composable () -> Unit) {
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    outAttributes.imeOptions =
                        outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    request.createInputConnection(outAttributes)
                }
            )
        }
    }

    InterceptPlatformTextInput(
        interceptor = interceptor,
        content = content
    )
}
