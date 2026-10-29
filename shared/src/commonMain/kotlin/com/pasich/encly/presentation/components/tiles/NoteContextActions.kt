package com.pasich.encly.presentation.components.tiles

import androidx.compose.runtime.Immutable
import com.pasich.encly.presentation.dialogs.NoteAction

/**
 * What a right click on a note card offers on a desktop: the actions of the long-press sheet
 * ([onAction], the same [NoteAction]s) and, where the host has tabs, [onOpenInNewTab].
 */
@Immutable
class NoteContextActions(val onAction: (NoteAction) -> Unit, val onOpenInNewTab: (() -> Unit)? = null)
