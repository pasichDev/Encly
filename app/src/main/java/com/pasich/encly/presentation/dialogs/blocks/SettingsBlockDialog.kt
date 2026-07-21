package com.pasich.encly.presentation.dialogs.blocks

import com.pasich.encly.dynamicBlocks.Block

data class SettingsBlockDialog(
    val block: Block, val blockMove: Int = -1, // 1 - up, 2 - down
    val isBottomSheetVisible: Boolean = false
)