package com.pasich.encly.presentation.components

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.core.view.inputmethod.EditorInfoCompat

/**
 * What Encly asks of every keyboard for its text fields.
 *
 * Always: [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] (do not learn from what is typed).
 * With [strict] ("Strict keyboard privacy" in Settings → Security): text fields also present
 * themselves as visible-password fields without suggestions, which compliant keyboards treat
 * as sensitive: no suggestions, no cloud prediction, no surrounding text handed over.
 *
 * The flags are applied after Compose has filled in the [EditorInfo], so they are not
 * overwritten by it. A third-party keyboard remains part of the device trust boundary and may
 * ignore any of this (see SECURITY.md).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SecureTextInputBoundary(strict: Boolean = false, content: @Composable () -> Unit) {
    val currentStrict by rememberUpdatedState(strict)
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    val connection = request.createInputConnection(outAttributes)
                    applyKeyboardPrivacy(outAttributes, currentStrict)
                    connection
                },
            )
        }
    }

    InterceptPlatformTextInput(
        interceptor = interceptor,
        content = content,
    )
}

/** Rewrites [info] for Encly's keyboard policy; see [SecureTextInputBoundary]. */
internal fun applyKeyboardPrivacy(info: EditorInfo, strict: Boolean) {
    info.imeOptions = info.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    if (!strict || info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
    info.inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
        (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    // Now a password type: this drops the surrounding text Compose already attached.
    EditorInfoCompat.setInitialSurroundingText(info, "")
}
