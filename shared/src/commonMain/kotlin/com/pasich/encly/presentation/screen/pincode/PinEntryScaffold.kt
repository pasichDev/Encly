package com.pasich.encly.presentation.screen.pincode

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyLogoMark
import com.pasich.encly.presentation.designsystem.KeypadSize
import com.pasich.encly.presentation.designsystem.PinDots
import com.pasich.encly.presentation.designsystem.PinFit
import com.pasich.encly.presentation.designsystem.PinKeypad
import com.pasich.encly.presentation.designsystem.PinnedFooter
import com.pasich.encly.presentation.designsystem.ProtectFromCapture
import com.pasich.encly.presentation.designsystem.authSurfaceColor
import com.pasich.encly.presentation.designsystem.pinFitFor
import com.pasich.encly.presentation.effects.LocalUnlockReveal
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * Full-screen loading shown while the auth screens do heavy off-thread work
 * (PBKDF2 PIN hashing, opening the encrypted database).
 */
@Composable
fun AuthLoading(message: String, modifier: Modifier = Modifier, inCard: Boolean = false) {
    Surface(modifier = modifier.fillMaxSize(), color = authSurfaceColor(inCard)) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
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
 * top with the logo (or [icon]'s tile), the headline and a sub-heading ([subtitleIsError] turns it `error` and
 * announces it to a screen reader, unless [subtitleTicks]: a countdown),
 * then the caller's [body] (dots and keypad, or the phrase field), which is given the [PinFit] of the room to hand on
 * to [PinEntry]. It scrolls on short screens.
 * [footer] (the recovery screen's buttons) stays pinned under the scrolling part, above the
 * keyboard, so a primary action is never hidden behind the IME. [compact] trades the 96 dp top
 * for 24 dp, under a top bar or for a form that needs the room. While [busy] (a credential being
 * checked) the logo breathes in place of a loading screen. With [bodyAtBottom] the body (the PIN dots
 * and keypad) is anchored to the bottom edge for one-handed use, not centred under the header.
 * On a short window (split-screen, a small phone, a large font) the top gap, the header, the dots and the keys
 * shrink with the height they are given, and the logo tile goes when even that is too tall ([pinFitFor]), so the
 * keypad and the action under it show without scrolling; below that the screen still scrolls.
 */
@Composable
fun PinEntryScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleIsError: Boolean = false,
    subtitleTicks: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
    busy: Boolean = false,
    bodyAtBottom: Boolean = false,
    inCard: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.(fit: PinFit) -> Unit,
) {
    ProtectFromCapture()
    val spacing = EnclyTheme.spacing
    val tone = when {
        !subtitleIsError -> SubtitleTone.Normal
        subtitleTicks -> SubtitleTone.Error
        else -> SubtitleTone.AnnouncedError
    }
    Surface(
        modifier = if (inCard) modifier.fillMaxWidth() else modifier.fillMaxSize(),
        color = authSurfaceColor(inCard),
    ) {
        Column(
            modifier = (if (inCard) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                // Below the status bar unless a parent (a top bar's Scaffold, a Column that
                // pads it) already consumed that inset.
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            BoxWithConstraints(modifier = if (inCard) Modifier.weight(1f, fill = false) else Modifier.weight(1f)) {
                val fit = remember(maxHeight, inCard) { if (inCard) PinFit.Full else pinFitFor(maxHeight) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // At least the screen high, so the header stays on top and the body (the keypad)
                    // sits on the bottom edge, within a thumb's reach; a short screen scrolls. In a card the
                    // page is as high as its content, and the action under it follows the form closely.
                    verticalArrangement = if (bodyAtBottom) Arrangement.SpaceBetween else Arrangement.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (inCard) Modifier else Modifier.heightIn(min = maxHeight))
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = lerp(spacing.xs, if (compact || inCard) spacing.l else spacing.lockTop, fit.fraction),
                            bottom = lerp(spacing.xs, spacing.l, fit.fraction),
                        ),
                ) {
                    PinEntryHeader(HeaderText(title, subtitle, tone), icon, busy, fit)
                    body(fit)
                }
            }
            if (footer != null) {
                PinnedFooter(restBottom = spacing.l, content = footer)
            }
        }
    }
}

/** How the sub-heading of a PIN screen reads: plain, an error, or an error a screen reader announces. */
private enum class SubtitleTone { Normal, Error, AnnouncedError }

/** The words of a PIN screen's header: the headline and the sub-heading with its [tone]. */
private class HeaderText(val title: String, val subtitle: String, val tone: SubtitleTone)

@Composable
private fun PinEntryHeader(text: HeaderText, icon: ImageVector?, busy: Boolean, fit: PinFit) {
    val spacing = EnclyTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(lerp(spacing.xxs, spacing.rowGap, fit.fraction)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter),
    ) {
        val reveal = LocalUnlockReveal.current
        if (!fit.showHeaderIcon) {
            // The logo tile is out of the layout: the unlock reveal has nothing to grow from and just navigates.
            SideEffect { reveal?.logoBounds = null }
        } else if (icon == null) {
            EnclyLogoMark(
                modifier = Modifier
                    .onGloballyPositioned { reveal?.logoBounds = it.boundsInRoot() }
                    .then(if (busy) Modifier.breathing() else Modifier),
            )
        } else {
            EnclyIconTile(icon = icon)
        }
        Text(
            text = text.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = lerp(0.dp, spacing.xs, fit.fraction)),
        )
        Text(
            text = text.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = if (text.tone != SubtitleTone.Normal) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            // A wrong PIN is read out by a screen reader as it appears; a lockout countdown changes every
            // second, so it would be read again at each tick.
            modifier = if (text.tone == SubtitleTone.AnnouncedError) {
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            } else {
                Modifier
            },
        )
    }
}

/** What a [PinEntry] reports: a digit, a deleted digit (or all of them), or a tap on the fingerprint key. */
class PinEntryActions(
    val onDigit: (Int) -> Unit,
    val onBackspace: () -> Unit,
    val onBiometric: (() -> Unit)? = null,
    /** Clears every entered digit (long-press on delete); without it, delete is repeated for a whole PIN. */
    val onClear: (() -> Unit)? = null,
)

/**
 * The button under a PIN keypad that submits what is typed. A PIN has no fixed length, so nothing is submitted by
 * itself: [enabled] is true from the shortest PIN's length on.
 */
class PinSubmit(val text: String, val enabled: Boolean, val onClick: () -> Unit)

/**
 * The PIN dots and the lock-size keypad, and the [submit] button under them. [error] turns the dots `error`; a new
 * [shakeKey] shakes them. [enabled] false (a lockout) dims and disables the keys and the button.
 */
@Composable
fun PinEntry(
    entered: Int,
    actions: PinEntryActions,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    shakeKey: Int = 0,
    enabled: Boolean = true,
    submit: PinSubmit? = null,
    fit: PinFit = PinFit.Full,
) {
    val spacing = EnclyTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        PinDots(
            entered = entered,
            error = error,
            shakeKey = shakeKey,
            modifier = Modifier.padding(
                top = lerp(spacing.s, spacing.lockDotsTop, fit.fraction),
                bottom = lerp(spacing.m, spacing.lockDotsBottom, fit.fraction),
            ),
        )
        PinKeypad(
            onDigit = actions.onDigit,
            onBackspace = actions.onBackspace,
            size = KeypadSize.LOCK,
            enabled = enabled,
            onBiometric = actions.onBiometric,
            onClear = actions.onClear,
            fit = fit,
        )
        if (submit != null) {
            EnclyButton(
                text = submit.text,
                onClick = submit.onClick,
                enabled = enabled && submit.enabled,
                modifier = Modifier.padding(
                    start = EnclyTheme.spacing.gutter,
                    end = EnclyTheme.spacing.gutter,
                    top = lerp(spacing.xs, spacing.l, fit.fraction),
                ),
            )
        }
    }
}

private const val BREATH_MS = 700
private const val BREATH_SCALE = 1.06f

/** A slow scale pulse, the lock screen's "checking" state. */
@Composable
private fun Modifier.breathing(): Modifier {
    val scale by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 1f,
        targetValue = BREATH_SCALE,
        animationSpec = infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse),
        label = "scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
