package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** Opacity of disabled content and of disabled containers (Material 3 standard). */
internal const val DISABLED_CONTENT_ALPHA = 0.38f
internal const val DISABLED_CONTAINER_ALPHA = 0.12f

/**
 * The primary action of a screen: full width, 52 dp, fully round, `primary` / `onPrimary`.
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
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = if (loading) {
                colors.primary
            } else {
                colors.onSurface.copy(
                    alpha = DISABLED_CONTAINER_ALPHA,
                )
            },
            disabledContentColor = if (loading) {
                colors.onPrimary
            } else {
                colors.onSurface.copy(
                    alpha = DISABLED_CONTENT_ALPHA,
                )
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.buttonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        } else {
            ButtonLabel(text = text, trailingIcon = trailingIcon)
        }
    }
}

@Composable
private fun ButtonLabel(text: String, trailingIcon: ImageVector?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
    ) {
        Text(text = text, style = EnclyTheme.typography.buttonPrimary)
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
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier
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
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = when {
                destructive -> colors.error
                muted -> colors.onSurfaceVariant
                else -> colors.primary
            },
        ),
        modifier = modifier.heightIn(min = EnclyTheme.spacing.textButtonHeight),
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
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** An outlined pill ("Hide words"): 44 dp, 1 dp `outlineVariant` border, optional leading icon. */
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
        shape = CircleShape,
        border = BorderStroke(EnclyTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.m),
        modifier = modifier.heightIn(min = EnclyTheme.spacing.textButtonHeight),
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
