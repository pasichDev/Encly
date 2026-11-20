package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One row of a context menu: [label], an optional leading [icon], the [onClick] it runs (the menu
 * closes first), and whether it is [destructive] (drawn in `error`) or not [enabled].
 */
@Immutable
data class MenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)
