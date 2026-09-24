package com.pasich.encly.presentation.screen.pincode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyLogoMark
import com.pasich.encly.presentation.designsystem.KeypadSize
import com.pasich.encly.presentation.designsystem.PinDots
import com.pasich.encly.presentation.designsystem.PinKeypad
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * Full-screen loading shown while the auth screens do heavy off-thread work
 * (PBKDF2 PIN hashing, opening the encrypted database).
 */
@Composable
fun AuthLoading(message: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.factGap),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The layout of every PIN and recovery screen (design spec §4.2): a centred column 96 dp from the
 * top with the logo (or [icon]'s tile), the headline and a sub-heading ([subtitleIsError] turns it `error`),
 * then the caller's [body] (dots and keypad, or the phrase field). It scrolls on short screens.
 * [footer] (the recovery screen's buttons) stays pinned under the scrolling part, above the
 * keyboard, so a primary action is never hidden behind the IME. [compact] trades the 96 dp top
 * for 24 dp, under a top bar or for a form that needs the room.
 */
@Composable
fun PinEntryScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleIsError: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val spacing = EnclyTheme.spacing
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.statusBarsPadding() else Modifier)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = if (compact) spacing.l else spacing.lockTop, bottom = spacing.l),
            ) {
                PinEntryHeader(title = title, subtitle = subtitle, subtitleIsError = subtitleIsError, icon = icon)
                body()
            }
            if (footer != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.s, bottom = spacing.l),
                    content = footer,
                )
            }
        }
    }
}

@Composable
private fun PinEntryHeader(title: String, subtitle: String, subtitleIsError: Boolean, icon: ImageVector?) {
    val spacing = EnclyTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter),
    ) {
        if (icon == null) {
            val reveal = LocalUnlockReveal.current
            EnclyLogoMark(modifier = Modifier.onGloballyPositioned { reveal.logoBounds = it.boundsInRoot() })
        } else {
            EnclyIconTile(icon = icon)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = if (subtitleIsError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** What a [PinEntry] reports: a digit, a deleted digit, or a tap on the fingerprint key. */
class PinEntryActions(val onDigit: (Int) -> Unit, val onBackspace: () -> Unit, val onBiometric: (() -> Unit)? = null)

/**
 * The PIN dots and the lock-size keypad. [error] turns the dots `error`; a new [shakeKey] shakes
 * them. [enabled] false (a lockout) dims and disables the keys.
 */
@Composable
fun PinEntry(
    entered: Int,
    actions: PinEntryActions,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    shakeKey: Int = 0,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PinDots(
            entered = entered,
            error = error,
            shakeKey = shakeKey,
            modifier = Modifier.padding(
                top = EnclyTheme.spacing.lockDotsTop,
                bottom = EnclyTheme.spacing.lockDotsBottom,
            ),
        )
        PinKeypad(
            onDigit = actions.onDigit,
            onBackspace = actions.onBackspace,
            size = KeypadSize.LOCK,
            enabled = enabled,
            onBiometric = actions.onBiometric,
        )
    }
}
