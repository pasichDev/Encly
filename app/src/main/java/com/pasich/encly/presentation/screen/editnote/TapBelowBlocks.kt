package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

/**
 * Calls [onTap] for a tap that no block took and that lands below the last item of [listState]'s
 * list: the empty page under a short note, which would otherwise refuse the tap while only a fixed
 * strip right under the last line took it.
 */
internal fun Modifier.tapBelowBlocks(listState: LazyListState, enabled: Boolean, onTap: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(listState) {
            detectTapGestures { position ->
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                val reachedEnd = last != null && last.index == info.totalItemsCount - 1
                if (reachedEnd && position.y > last.offset + last.size) onTap()
            }
        }
    }

/**
 * Writing at the end of the note: a paragraph there takes focus, and the keyboard comes up even where
 * the last field kept its focus after the keyboard was hidden (focusing it again shows nothing).
 */
@Composable
internal fun rememberWriteAtEnd(viewModel: EditNoteViewModel): () -> Unit {
    val keyboardController = LocalSoftwareKeyboardController.current
    return {
        viewModel.addBlockToEnd()
        keyboardController?.show()
    }
}
