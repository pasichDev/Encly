package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.graphics.vector.ImageVector

/** The glyphs of what only a host with several windows and a printer offers, in the grammar of [EnclyIcons]. */
object EnclyDesktopIcons {
    /** A printer: print the note. */
    val Print: ImageVector by lazy {
        enclyIcon(
            "Print",
            "M7 9V4h10v5",
            "M7 17H5.5A1.5 1.5 0 0 1 4 15.5v-5A1.5 1.5 0 0 1 5.5 9h13a1.5 1.5 0 0 1 1.5 1.5" +
                "v5a1.5 1.5 0 0 1-1.5 1.5H17",
            "M7 14h10v6H7z",
        )
    }

    /** A window with its title bar: open in a new window. */
    val Window: ImageVector by lazy {
        enclyIcon(
            "Window",
            "M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5" +
                "h-13A1.5 1.5 0 0 1 4 18.5z",
            "M4 9h16",
        )
    }
}
