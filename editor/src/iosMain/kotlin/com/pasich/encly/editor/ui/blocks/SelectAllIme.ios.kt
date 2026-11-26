package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable

@Composable
internal actual fun BlockSelectAllIme(
    onSelectAll: () -> Boolean,
    onCopy: () -> Boolean,
    onCut: () -> Boolean,
    content: @Composable () -> Unit,
) = content()
