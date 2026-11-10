package com.pasich.encly.presentation.screen.importnotes

import com.pasich.encly.data.importer.ImportSource
import com.pasich.encly.presentation.viewmodel.ReviewFilter

/** What the page's buttons do; the screen wires them to the ViewModel and the system pickers. */
class ImportNotesActions(
    val onChooseFiles: () -> Unit,
    val onChooseFolder: () -> Unit,
    val onImport: () -> Unit,
    val onCancel: () -> Unit,
    val onChooseSource: (ImportSource) -> Unit,
    val onToggle: (String) -> Unit = {},
    val onToggleAll: () -> Unit = {},
    val onFilter: (ReviewFilter) -> Unit = {},
    val onExpand: (String) -> Unit = {},
)
