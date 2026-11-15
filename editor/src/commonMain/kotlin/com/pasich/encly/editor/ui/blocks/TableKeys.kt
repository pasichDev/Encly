package com.pasich.encly.editor.ui.blocks

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.editor.state.CellStep

/** The cell a Left or Right [key] leads to: the one on that side of the screen, whichever way the layout runs. */
internal fun sideStep(key: Key, layoutDirection: LayoutDirection): CellStep =
    if ((key == Key.DirectionLeft) != (layoutDirection == LayoutDirection.Rtl)) CellStep.PREVIOUS else CellStep.NEXT
