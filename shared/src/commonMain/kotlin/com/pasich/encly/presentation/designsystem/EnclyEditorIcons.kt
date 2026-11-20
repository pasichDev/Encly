package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.graphics.vector.ImageVector

// The editor's block icons for code and tables, kept apart from EnclyIcons, which is as large as a class may be.

private val CodeIcon: ImageVector by lazy {
    enclyIcon(
        "Code",
        "M8 8l-4 4 4 4",
        "M16 8l4 4-4 4",
        "M13.5 6l-3 12",
    )
}

private val TableIcon: ImageVector by lazy {
    enclyIcon(
        "Table",
        "M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z",
        "M4 10.5h16M4 14.8h16M10 5v14",
    )
}

/** The `< >` of a code block. */
val EnclyIcons.Code: ImageVector get() = CodeIcon

/** A grid of rows and columns, for a table block. */
val EnclyIcons.Table: ImageVector get() = TableIcon
