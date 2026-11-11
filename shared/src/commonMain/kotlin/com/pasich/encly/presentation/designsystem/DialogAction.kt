package com.pasich.encly.presentation.designsystem

/**
 * A button by what it says and does. In a dialog: a text button in `primary`, or in `error` when
 * [destructive]; in an [EnclyButtonPair] its place says which of the two it is.
 */
class DialogAction(
    val text: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)
