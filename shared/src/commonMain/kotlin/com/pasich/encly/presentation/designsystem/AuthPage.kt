package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pasich.encly.presentation.pane.CenteredContent
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.LocalPointerFirst
import com.pasich.encly.ui.theme.LocalWidthClass

/** Where the card sits in the window: a little above the centre, where the eye rests on a wide screen. */
private const val CARD_VERTICAL_BIAS = -0.2f

/**
 * Whether the first-run, lock and recovery pages are drawn as a card in the middle of the window: a host driven by
 * a mouse and keyboard ([LocalPointerFirst]) and a window that is at least medium wide. Everywhere else, every touch
 * host and every compact window, those pages keep the phone layout they always had.
 */
@Composable
fun authCardApplies(): Boolean = LocalPointerFirst.current && LocalWidthClass.current.isWide

/** The colour a page is painted in: the card's own inside [AuthPage]'s card, the window's `surface` otherwise. */
@Composable
internal fun authSurfaceColor(inCard: Boolean): Color =
    if (inCard) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surface

/**
 * A first-run, lock or recovery page ([content]): where [authCardApplies] it stands in a card of
 * [com.pasich.encly.ui.theme.EnclySpacing.authCardWidth], as high as its content and centred in the window, with
 * the primary action under the form instead of at the window's bottom edge. Elsewhere it is [CenteredContent],
 * the page as a phone shows it.
 *
 * The page inside takes its height from its content (no `fillMaxSize`) and scrolls its own body when the window is
 * too low, keeping the action under it in reach; see `inCard` of the screens.
 */
@Composable
fun AuthPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (authCardApplies()) {
        val spacing = EnclyTheme.spacing
        Box(
            contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = CARD_VERTICAL_BIAS),
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = authSurfaceColor(inCard = true),
                border = BorderStroke(spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(spacing.m).widthIn(max = spacing.authCardWidth).fillMaxWidth(),
                content = content,
            )
        }
    } else {
        CenteredContent(modifier = modifier, content = content)
    }
}
