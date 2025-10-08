package com.pasich.encly.presentation.dialogs.blocks

sealed class ActionBlockDialog {
    data class Move(val move: Int) : ActionBlockDialog() // 1 - top, 2 - bottom
    object Delete : ActionBlockDialog()
}