package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable

@Composable
internal actual fun BlockSelectAllIme(onSelectAll: () -> Boolean, content: @Composable () -> Unit) = content()
