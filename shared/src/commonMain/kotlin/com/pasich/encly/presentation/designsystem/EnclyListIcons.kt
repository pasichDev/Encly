package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.graphics.vector.ImageVector

// The icons of nested lists. Drawn like the others (see enclyIcon), mirrored in a right-to-left layout.

private val IndentIcon: ImageVector by lazy {
    enclyIcon(
        "Indent",
        "M4 5.5h16M12 10h8M12 14h8M4 18.5h16",
        "M4 9.5l3 2.5-3 2.5",
    )
}

private val OutdentIcon: ImageVector by lazy {
    enclyIcon(
        "Outdent",
        "M4 5.5h16M12 10h8M12 14h8M4 18.5h16",
        "M7 9.5l-3 2.5 3 2.5",
    )
}

/** Moves a list item one level deeper. */
internal val EnclyIcons.Indent: ImageVector get() = IndentIcon

/** Brings a list item out one level. */
internal val EnclyIcons.Outdent: ImageVector get() = OutdentIcon
