package com.pasich.encly.editor.ui.blocks

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun BlockSelectAllIme(onSelectAll: () -> Boolean, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(onSelectAll)
    val interceptor = remember {
        PlatformTextInputInterceptor { request, next -> next.startInputMethod(selectAllRequest(request) { current() }) }
    }
    InterceptPlatformTextInput(interceptor, content)
}

/** [request], except that the connection it makes is wrapped in a [SelectAllConnection]. */
internal fun selectAllRequest(request: PlatformTextInputMethodRequest, onSelectAll: () -> Boolean) =
    PlatformTextInputMethodRequest { info ->
        SelectAllConnection(request.createInputConnection(info), onSelectAll)
    }

/**
 * The field's connection to the keyboard, except that the Select all menu action, asked while the text is
 * all selected already (or empty), runs [onSelectAll] instead. When that declines, or only part of the text
 * is selected, the field handles the action as always.
 */
internal class SelectAllConnection(target: InputConnection, private val onSelectAll: () -> Boolean) :
    InputConnectionWrapper(target, false) {
    override fun performContextMenuAction(id: Int): Boolean =
        if (id == android.R.id.selectAll && textFullySelected() && onSelectAll()) {
            true
        } else {
            super.performContextMenuAction(id)
        }

    private fun textFullySelected(): Boolean {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val start = minOf(extracted.selectionStart, extracted.selectionEnd)
        val end = maxOf(extracted.selectionStart, extracted.selectionEnd)
        return extracted.text.isEmpty() || (start == 0 && end == extracted.text.length)
    }
}
