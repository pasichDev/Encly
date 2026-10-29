package com.pasich.encly.presentation.screen.editnote

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.note_archive_failed
import com.pasich.encly.core.resources.note_delete_failed
import com.pasich.encly.core.resources.note_discard_failed
import com.pasich.encly.core.resources.note_duplicate_failed
import com.pasich.encly.core.resources.note_restore_failed
import com.pasich.encly.core.resources.note_save_failed
import com.pasich.encly.core.resources.note_trash_failed
import com.pasich.encly.core.resources.note_unarchive_failed
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Every way out of the editor: back (saves), restore, delete, discard, trash, archive and duplicate. Each
 * writes first and leaves only once the write succeeded; a failure keeps the editor open and is
 * reported through [onFailure]. The first exit wins: a second Back (gesture and arrow) or any
 * other exit tapped meanwhile is ignored, so the editor never pops past the notes list.
 */
@Suppress("TooManyFunctions") // One function per way out.
internal class EditorExits(
    private val viewModel: EditNoteViewModel,
    private val navController: NavHostController,
    private val entry: NavBackStackEntry?,
    private val scope: CoroutineScope,
    private val onFailure: (StringResource) -> Unit,
    private val beforeLeaving: () -> Unit,
) {
    private var leaving = false

    fun back() = leave(Res.string.note_save_failed, viewModel::saveNote)

    fun restore() = leave(Res.string.note_restore_failed, viewModel::noteRestore)

    fun delete() = leave(Res.string.note_delete_failed, viewModel::noteDelete)

    fun trash() = leave(Res.string.note_trash_failed, viewModel::noteMoveToTrash)

    /** Moves the note to the archive (or, when it is archived, back to the list) and leaves. */
    fun archive(archived: Boolean) {
        if (viewModel.isBlankDraft) return
        leave(
            failure = if (archived) Res.string.note_archive_failed else Res.string.note_unarchive_failed,
            action = { viewModel.saveNote() && viewModel.noteSetArchived(archived) },
        )
    }

    /**
     * Saves the note, then hands its id to [start], which begins the plaintext export
     * (warning, PIN, save dialog). The editor stays open. Nothing to export from a blank new note
     * or a note whose stored content could not be read.
     */
    fun exportMarkdown(start: (id: Long) -> Unit) {
        if (viewModel.isBlankDraft || viewModel.contentLoadFailed.value) return
        scope.launch {
            if (viewModel.saveNote() && viewModel.state.value.note.id > 0L) {
                start(viewModel.state.value.note.id)
            } else {
                onFailure(Res.string.note_save_failed)
            }
        }
    }

    fun discard() = leave(Res.string.note_discard_failed, viewModel::discardChanges)

    /** Discards at once when nothing would be lost; otherwise asks first ([confirm]). */
    fun discardOrConfirm(confirm: () -> Unit) {
        scope.launch { if (viewModel.hasChanges()) confirm() else discard() }
    }

    /** Stores a copy and opens it in place of this editor. A blank new note has nothing to copy. */
    fun duplicate() {
        if (viewModel.isBlankDraft) return
        var copyId = -1L
        leave(
            failure = Res.string.note_duplicate_failed,
            action = {
                copyId = if (viewModel.saveNote()) viewModel.noteDuplicate() else -1L
                copyId > 0L
            },
            onDone = { openCopy(copyId) },
        )
    }

    private fun leave(failure: StringResource, action: suspend () -> Boolean, onDone: () -> Unit = ::pop) {
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
