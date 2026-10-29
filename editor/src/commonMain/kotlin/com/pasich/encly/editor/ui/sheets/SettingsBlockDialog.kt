package com.pasich.encly.editor.ui.sheets

import com.pasich.encly.editor.model.Block

internal data class SettingsBlockDialog(
    val block: Block,
    val blockMove: Int = -1, // 1 - up, 2 - down
    val isBottomSheetVisible: Boolean = false,
    // False in a read-only editor: only actions that do not change the note are offered.
    val canEdit: Boolean = true,
)
