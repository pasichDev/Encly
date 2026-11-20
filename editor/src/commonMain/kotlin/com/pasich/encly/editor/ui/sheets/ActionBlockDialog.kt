package com.pasich.encly.editor.ui.sheets

internal sealed class ActionBlockDialog {
    data class Move(val move: Int) : ActionBlockDialog() // 1 - top, 2 - bottom
    object Duplicate : ActionBlockDialog()
    object Delete : ActionBlockDialog()
    object OpenLink : ActionBlockDialog()
    object CopyLink : ActionBlockDialog()
    object EditLink : ActionBlockDialog()
}
