package com.pasich.encly.editor.ui.blocks

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun NoSuggestionsInput(content: @Composable () -> Unit) {
    val interceptor = remember {
        PlatformTextInputInterceptor { request, next -> next.startInputMethod(noSuggestionsRequest(request)) }
    }
    InterceptPlatformTextInput(interceptor, content)
}

/** [request], except that the field it makes asks the keyboard for no suggestions. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun noSuggestionsRequest(request: PlatformTextInputMethodRequest) = PlatformTextInputMethodRequest { info ->
    request.createInputConnection(info).also { askForNoSuggestions(info) }
}

/** Adds the no-suggestions flag to a text field's [info]; other kinds of input are left alone. */
internal fun askForNoSuggestions(info: EditorInfo) {
    if (info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
        info.inputType = info.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
}
