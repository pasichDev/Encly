package com.pasich.encly.presentation.components.tiles

import androidx.compose.runtime.Immutable
import com.pasich.encly.presentation.dialogs.NoteAction

/**
 * What a right click on a note card offers on a desktop: the actions of the long-press sheet
 * ([onAction], the same [NoteAction]s) and, where the host has them, [onOpenInNewTab] (tabs), [onOpenInNewWindow]
 * (several windows) and [onExportPdf] and [onPrint] (a PDF writer and a printer). A null entry is not offered.
 */
@Immutable
class NoteContextActions(
    val onAction: (NoteAction) -> Unit,
    val onOpenInNewTab: (() -> Unit)? = null,
    val onOpenInNewWindow: (() -> Unit)? = null,
    val onExportPdf: (() -> Unit)? = null,
    val onPrint: (() -> Unit)? = null,
)
