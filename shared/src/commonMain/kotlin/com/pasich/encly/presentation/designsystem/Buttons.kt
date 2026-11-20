package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** Opacity of disabled content and of disabled containers (Material 3 standard). */
internal const val DISABLED_CONTENT_ALPHA = 0.38f
internal const val DISABLED_CONTAINER_ALPHA = 0.12f

/**
 * The corner of every button, filled, tonal, outlined or text: the FAB's (design spec §3.1, radius 18).
 * Chips, segments, icon buttons and the keypad stay fully round.
 */
val EnclyButtonShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.shapes.large

/**
 * The primary action of a screen: full width, 52 dp, the button corner, `primary` / `onPrimary`.
 * [loading] replaces the label with a progress indicator and blocks taps.
 */
@Composable
fun EnclyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    FilledButton(
        onClick = onClick,
        container = colors.primary,
        content = colors.onPrimary,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
    ) {
        ButtonLabel(text = text, trailingIcon = trailingIcon)
    }
}

/**
 * A destructive action and the one that confirms its sheet, side by side ("Delete task" · "Save"): two
 * halves of the same height and corner, [EnclyTheme.spacing.s] apart, the destructive one first in red
 * (`error`, or `errorContainer` in a dark scheme), the confirming one the primary button. A label that does
 * not fit half the width wraps to a second line instead of losing its end ("Delete forever" in French).
 */
@Composable
fun EnclyButtonPair(destructive: DialogAction, confirm: DialogAction, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Red in both modes: a dark scheme's `error` is a pale pink that reads as a second primary beside Save,
    // so there the deep `errorContainer` is the fill.
    val pale = colors.error.luminance() > PALE_FILL_LUMINANCE
    Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s), modifier = modifier.fillMaxWidth()) {
        FilledButton(
            onClick = destructive.onClick,
            container = if (pale) colors.errorContainer else colors.error,
            content = if (pale) colors.onErrorContainer else colors.onError,
            modifier = Modifier.weight(1f),
            enabled = destructive.enabled,
            padding = PairPadding,
        ) {
            ButtonLabel(text = destructive.text, trailingIcon = null, maxLines = PAIR_LABEL_LINES)
        }
        FilledButton(
            onClick = confirm.onClick,
            container = colors.primary,
            content = colors.onPrimary,
            modifier = Modifier.weight(1f),
            enabled = confirm.enabled,
            padding = PairPadding,
        ) {
            ButtonLabel(text = confirm.text, trailingIcon = null, maxLines = PAIR_LABEL_LINES)
        }
    }
}

/** A paired label wraps rather than ellipsizes. */
private const val PAIR_LABEL_LINES = 2

/** Above this a fill is too light to carry a destructive button by itself. */
private const val PALE_FILL_LUMINANCE = 0.4f

/** Half-width buttons give their labels the room a full-width one spends on its sides. */
private val PairPadding = PaddingValues(horizontal = 12.dp)

@Composable
private fun FilledButton(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    padding: PaddingValues = ButtonDefaults.ContentPadding,
    label: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = EnclyButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (loading) {
                container
            } else {
                colors.onSurface.copy(
                    alpha = DISABLED_CONTAINER_ALPHA,
                )
            },
            disabledContentColor = if (loading) content else colors.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
        ),
        contentPadding = padding,
        modifier = modifier
            .enclyInteractive(EnclyButtonShape, enabled && !loading)
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.buttonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        } else {
            label()
        }
    }
}

@Composable
private fun ButtonLabel(text: String, trailingIcon: ImageVector?, maxLines: Int = 1) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
    ) {
        Text(
            text = text,
            style = EnclyTheme.typography.buttonPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(EnclyTheme.spacing.iconSmall))
        }
    }
}

/** A secondary full-width action: 52 dp, `primaryContainer` / `onPrimaryContainer`. */
@Composable
fun EnclyTonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = EnclyButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier
            .enclyInteractive(EnclyButtonShape, enabled)
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.buttonHeight),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A text action, 44 dp tall. [muted] uses `onSurfaceVariant` for the de-emphasised escape hatch
 * ("Skip: PIN only, no backups"), [destructive] `error`; otherwise the label is `primary`.
 */
@Composable
fun EnclyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = EnclyButtonShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = when {
                destructive -> colors.error
                muted -> colors.onSurfaceVariant
                else -> colors.primary
            },
        ),
        modifier = modifier.enclyInteractive(
            EnclyButtonShape,
            enabled,
        ).heightIn(min = EnclyTheme.spacing.textButtonHeight),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = EnclyTheme.spacing.xs)
                    .size(EnclyTheme.spacing.iconSmall),
            )
        }
        // Centred, so a label that wraps at a large font scale keeps the screen's alignment.
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** An outlined button ("Hide words"): 44 dp, the button corner, 1 dp `outlineVariant` border, optional leading icon. */
@Composable
fun EnclyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = EnclyButtonShape,
        border = BorderStroke(EnclyTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.m),
        modifier = modifier.enclyInteractive(
            EnclyButtonShape,
            enabled,
        ).heightIn(min = EnclyTheme.spacing.textButtonHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(EnclyTheme.spacing.iconSmall))
            }
            Text(text = text, style = EnclyTheme.typography.chip)
        }
    }
}
