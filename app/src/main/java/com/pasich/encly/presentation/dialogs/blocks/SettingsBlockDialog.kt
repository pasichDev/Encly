package com.pasich.encly.presentation.dialogs.blocks

import com.pasich.encly.dynamicBlocks.Block

data class SettingsBlockDialog(
    val block: Block, val blockMove: Int = -1, // 1 - вверх, 2 - вниз
    val isBottomSheetVisible: Boolean = false
)