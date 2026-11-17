package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.pasich.encly.ui.theme.EnclyTheme

/** True while the soft keyboard takes any of the screen's height. */
@Composable
internal fun rememberImeOpen(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

/**
 * The action area under a form's scrolling part: a primary button, and below it a text action or
 * (with [reserveTextRow]) the same space left empty, so the primary never moves between steps.
 *
 * It is a sibling of the scrolling part, never laid over it, so the form always ends above it. While
 * the keyboard is open the room is the form's: the empty row and the roomy bottom margin go, so one
 * more field fits above the button. Every "form, then a button above the keyboard" screen uses this.
 */
@Composable
internal fun PinnedFooter(
    modifier: Modifier = Modifier,
    reserveTextRow: Boolean = false,
    restBottom: Dp = EnclyTheme.spacing.stepGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = EnclyTheme.spacing
    val imeOpen = rememberImeOpen()
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = spacing.gutter,
                end = spacing.gutter,
                top = spacing.s,
                bottom = if (imeOpen) spacing.s else restBottom,
            ),
    ) {
        content()
        if (reserveTextRow && !imeOpen) Spacer(modifier = Modifier.height(spacing.textButtonHeight))
    }
}
