package com.pasich.encly.editor.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

/** One row of an [EditorContextMenu]. */
internal class ContextEntry(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
