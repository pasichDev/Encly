package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** Size of the circle behind a fact or done icon. */
private val FactCircle = 44.dp
private val DoneCircle = 28.dp

/** The screen title and its muted lead paragraph, shared by every step. */
@Composable
fun StepHeading(title: String, body: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        Text(
            text = title,
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A Welcome fact: an icon in a `primaryContainer` circle, a title and a description. */
@Composable
fun FactRow(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m), modifier = modifier.fillMaxWidth()) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(FactCircle)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
                )
            }
        }
        TitleAndDescription(title, description, MaterialTheme.typography.titleSmall)
    }
}

/** A "what the phrase is for" row: hairline above, icon in `primary`, title and description. */
@Composable
fun UseRow(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = EnclyTheme.spacing.hairline, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap),
            modifier = Modifier.padding(vertical = EnclyTheme.spacing.rowGap),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
            )
            TitleAndDescription(title, description, MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * A Ready summary row: a check in a `primary` circle, or with [done] false an info mark on a
 * neutral circle, for what was deliberately left out.
 */
@Composable
fun DoneRow(title: String, description: String, modifier: Modifier = Modifier, done: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap), modifier = modifier.fillMaxWidth()) {
        Surface(shape = CircleShape, color = if (done) colors.primary else colors.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(DoneCircle)) {
                Icon(
                    if (done) EnclyIcons.CheckBold else EnclyIcons.Info,
                    contentDescription = null,
                    tint = if (done) colors.onPrimary else colors.onSurface,
                    modifier = Modifier.size(EnclyTheme.spacing.m),
                )
            }
        }
        TitleAndDescription(title, description, MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TitleAndDescription(title: String, description: String, titleStyle: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap)) {
        Text(text = title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The backup file picker: file icon, the picked file's name (or a prompt), a hint and a chevron. */
@Composable
fun FilePickerButton(
    title: String,
    hint: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(EnclyTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = EnclyTheme.spacing.m, vertical = EnclyTheme.spacing.s),
        ) {
            Icon(
                EnclyIcons.File,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                EnclyIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
    }
}

/** A form section: the numbered overline, then its content. */
@Composable
fun FormSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
