package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pasich.encly.platform.LocalNoteEditClaims
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first

/**
 * In a host with several windows, a note has one editor at a time (see `NoteEditRegistry`): this editor claims its
 * note while it is on screen. When another window already edits it, this one becomes a read-only view of the
 * stored note, which follows what the other window saves, and says so, instead of saving over the other window's
 * work. When that window gives the note back this one claims it again and, if the stored note changed meanwhile,
 * shows the stored text. The claim is given back only after this editor's last changes are saved. The note's id is
 * only known once a new note was saved, so the claim follows it. Without claims (Android) nothing happens.
 */
@Composable
internal fun ClaimNoteForEditing(viewModel: EditNoteViewModel = metroViewModel()) {
    val claims = LocalNoteEditClaims.current ?: return
    val state = viewModel.state.collectAsState()
    val noteId by remember(state) { derivedStateOf { state.value.note.id } }
    if (viewModel.isReadTrashOnly || noteId <= 0L) return
    var retry by remember(noteId) { mutableIntStateOf(0) }
    var holding by remember(noteId) { mutableStateOf(true) }
    var seenReleases by remember(noteId) { mutableLongStateOf(0L) }
    DisposableEffect(claims, viewModel, noteId, retry) {
        seenReleases = claims.releaseCount()
        val granted = claims.claim(noteId)
        holding = granted
        viewModel.onClaimResult(granted)
        onDispose { if (granted) viewModel.finishEditing { claims.release(noteId) } }
    }
    if (!holding) {
        // Another window holds the note: try again as soon as any claim is given back.
        LaunchedEffect(claims, noteId, seenReleases) {
            claims.releases.first { it != seenReleases }
            retry++
        }
    }
}
