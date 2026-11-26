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
internal actual fun BlockSelectAllIme(
    onSelectAll: () -> Boolean,
    onCopy: () -> Boolean,
    onCut: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val selectAll by rememberUpdatedState(onSelectAll)
    val copy by rememberUpdatedState(onCopy)
    val cut by rememberUpdatedState(onCut)
    val interceptor = remember {
        PlatformTextInputInterceptor { request, next ->
            next.startInputMethod(selectAllRequest(request, { selectAll() }, { copy() }, { cut() }))
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}

/** [request], except that the connection it makes is wrapped in a [SelectAllConnection]. */
internal fun selectAllRequest(
    request: PlatformTextInputMethodRequest,
    onSelectAll: () -> Boolean,
    onCopy: () -> Boolean = { false },
    onCut: () -> Boolean = { false },
) = PlatformTextInputMethodRequest { info ->
    SelectAllConnection(request.createInputConnection(info), onSelectAll, onCopy, onCut)
}

/**
 * The field's connection to the keyboard, except that the Select all menu action, asked while the text is
 * all selected already (or empty), runs [onSelectAll] instead. When that declines, or only part of the text
 * is selected, the field handles the action as always.
 *
 * Copy and Cut run [onCopy] and [onCut] first: the field's own selection is only the paragraph the first Select all
 * made, so while a selection runs across blocks it is the editor that has to copy or cut it. When they decline
 * (there is no such selection) the field copies or cuts its own.
 */
internal class SelectAllConnection(
    target: InputConnection,
    private val onSelectAll: () -> Boolean,
    private val onCopy: () -> Boolean = { false },
    private val onCut: () -> Boolean = { false },
) : InputConnectionWrapper(target, false) {
    override fun performContextMenuAction(id: Int): Boolean = when {
        id == android.R.id.selectAll && textFullySelected() && onSelectAll() -> true
        id == android.R.id.copy && onCopy() -> true
        id == android.R.id.cut && onCut() -> true
        else -> super.performContextMenuAction(id)
    }

    private fun textFullySelected(): Boolean {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val start = minOf(extracted.selectionStart, extracted.selectionEnd)
        val end = maxOf(extracted.selectionStart, extracted.selectionEnd)
        return extracted.text.isEmpty() || (start == 0 && end == extracted.text.length)
    }
}
