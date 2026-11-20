package com.pasich.encly.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.awt.Frame
import java.nio.file.Path

/**
 * A second start of the app (see `InstanceChannel`) arrives in [activations] with the files it was given, maybe
 * none: the window that was active last comes to the front and takes the files like ones dropped on it, once it can
 * ([settle] pauses after an unlock, see [handOverFiles]).
 */
@Composable
internal fun ActivationEffect(
    activations: Flow<List<Path>>,
    windows: DesktopWindows,
    locked: StateFlow<Boolean>,
    settle: suspend () -> Unit,
) {
    LaunchedEffect(activations) {
        activations.collect { files ->
            val window = windows.focused ?: return@collect
            window.frame?.let(::bringToFront)
            if (files.isNotEmpty()) launch { window.services.handOverFiles(locked, files, settle) }
        }
    }
}

/** Brings [frame] to the front of the other applications' windows, restoring it first if it was minimised. */
internal fun bringToFront(frame: java.awt.Window) {
    if (frame is Frame && frame.extendedState and Frame.ICONIFIED != 0) {
        frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
    }
    frame.toFront()
    frame.requestFocus()
}
