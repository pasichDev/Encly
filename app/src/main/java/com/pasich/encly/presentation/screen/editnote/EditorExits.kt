package com.pasich.encly.presentation.screen.editnote

import androidx.annotation.StringRes
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every way out of the editor: back (saves), restore, delete, discard, trash and duplicate. Each
 * writes first and leaves only once the write succeeded; a failure keeps the editor open and is
 * reported through [onFailure]. The first exit wins: a second Back (gesture and arrow) or any
 * other exit tapped meanwhile is ignored, so the editor never pops past the notes list.
 */
internal class EditorExits(
    private val viewModel: EditNoteViewModel,
    private val navController: NavHostController,
    private val entry: NavBackStackEntry?,
    private val scope: CoroutineScope,
    private val onFailure: (Int) -> Unit,
    private val beforeLeaving: () -> Unit,
) {
    private var leaving = false

    fun back() = leave(R.string.note_save_failed, viewModel::saveNote)

    fun restore() = leave(R.string.note_restore_failed, viewModel::noteRestore)

    fun delete() = leave(R.string.note_delete_failed, viewModel::noteDelete)

    fun trash() = leave(R.string.note_trash_failed, viewModel::noteMoveToTrash)

    fun discard() = leave(R.string.note_discard_failed, viewModel::discardChanges)

    /** Discards at once when nothing would be lost; otherwise asks first ([confirm]). */
    fun discardOrConfirm(confirm: () -> Unit) {
        scope.launch { if (viewModel.hasChanges()) confirm() else discard() }
    }

    /** Stores a copy and opens it in place of this editor. A blank new note has nothing to copy. */
    fun duplicate() {
        if (viewModel.isBlankDraft) return
        var copyId = -1L
        leave(
            failure = R.string.note_duplicate_failed,
            action = {
                copyId = if (viewModel.saveNote()) viewModel.noteDuplicate() else -1L
                copyId > 0L
            },
            onDone = { openCopy(copyId) },
        )
    }

    private fun leave(@StringRes failure: Int, action: suspend () -> Boolean, onDone: () -> Unit = ::pop) {
        if (leaving) return
        leaving = true
        scope.launch {
            if (action()) {
                onDone()
            } else {
                leaving = false
                onFailure(failure)
            }
        }
    }

    // Pops only this editor, and only while it is on top.
    private fun pop() {
        viewModel.markExitHandled()
        beforeLeaving()
        if (entry == null || navController.currentBackStackEntry == entry) navController.popBackStack()
    }

    private fun openCopy(copyId: Long) {
        viewModel.markExitHandled()
        beforeLeaving()
        navController.navigate("${NavRoutes.EditNoteRoute.name}/$copyId") {
            entry?.let { popUpTo(it.destination.id) { inclusive = true } }
        }
    }
}
