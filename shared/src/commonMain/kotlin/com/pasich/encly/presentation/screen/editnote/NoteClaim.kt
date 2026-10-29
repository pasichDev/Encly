package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.pasich.encly.platform.LocalNoteEditClaims
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * In a host with several windows, a note has one editor at a time (see `NoteEditRegistry`): this editor claims its
 * note while it is on screen. When another window already edits it, this one becomes a read-only view of the
 * stored note and says so, instead of saving over the other window's work. The note's id is only known once a new
 * note was saved, so the claim follows it. Without claims (Android) nothing happens.
 */
@Composable
internal fun ClaimNoteForEditing(viewModel: EditNoteViewModel = metroViewModel()) {
    val claims = LocalNoteEditClaims.current ?: return
    val state = viewModel.state.collectAsState()
    val noteId by remember(state) { derivedStateOf { state.value.note.id } }
    if (viewModel.isReadTrashOnly || noteId <= 0L) return
    DisposableEffect(claims, viewModel, noteId) {
        val granted = claims.claim(noteId)
        viewModel.setEditedElsewhere(!granted)
        onDispose { if (granted) claims.release(noteId) }
    }
}
