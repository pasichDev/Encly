package com.pasich.encly.presentation.designsystem

/** A dialog button: a text button in `primary`, or in `error` when [destructive]. */
class DialogAction(
    val text: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)
