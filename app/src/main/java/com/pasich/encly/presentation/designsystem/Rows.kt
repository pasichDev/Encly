package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.pasich.encly.ui.theme.EnclyTheme

/** Switch colours of the design system: `primary` track when on, `surfaceContainerHigh` when off. */
@Composable
fun enclySwitchColors(): SwitchColors {
    val c = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedTrackColor = c.primary,
        checkedBorderColor = c.primary,
        checkedThumbColor = c.onPrimary,
        uncheckedTrackColor = c.surfaceContainerHigh,
        uncheckedBorderColor = c.outline,
        uncheckedThumbColor = c.outline,
    )
}

/** The shared layout of settings rows: optional leading icon, title and supporting text, trailing slot. */
@Composable
private fun RowLayout(
    title: String,
    supporting: String?,
    icon: ImageVector?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.rowHeight)
            .padding(horizontal = EnclyTheme.spacing.xxs, vertical = EnclyTheme.spacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.primary.disabled(),
                modifier = Modifier.size(EnclyTheme.spacing.icon),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.onSurface else colors.onSurface.disabled(),
            )
            if (supporting != null) {
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** A settings row whose whole area toggles the trailing switch. */
@Composable
fun EnclySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    RowLayout(
        title = title,
        supporting = supporting,
        icon = icon,
        enabled = enabled,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = enclySwitchColors())
    }
}

/** A settings row that opens another screen; trailing chevron. */
@Composable
fun EnclyNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    RowLayout(
        title = title,
        supporting = supporting,
        icon = icon,
        enabled = true,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
        )
    }
}

/**
 * A list row with the settings-row metrics (min 56 dp, 14 dp gap): optional leading icon in
 * `primary`, title and supporting text, and a [trailing] slot. With [onClick] the whole row is
 * the tap target.
 */
@Composable
fun EnclyListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    RowLayout(
        title = title,
        supporting = supporting,
        icon = icon,
        enabled = true,
        modifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier,
        trailing = trailing,
    )
}

/** A 1 dp `outlineVariant` hairline between the rows of one grouped list. */
@Composable
fun EnclyGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        thickness = EnclyTheme.spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.padding(horizontal = EnclyTheme.spacing.m),
    )
}

/**
 * A radio option (font choice, sort order, language): an optional [leading] slot, title and
 * description, and the radio at the end; min 60 dp, padding 8/16, 14 dp gap.
 */
@Composable
fun EnclyRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = EnclyTheme.spacing.m, vertical = EnclyTheme.spacing.xs),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        EnclyRadio(selected = selected)
    }
}

/** A 22 dp radio: 2 dp ring (`primary` when selected, `outline` otherwise) and a 10 dp dot. */
@Composable
fun EnclyRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(EnclyTheme.spacing.iconMedium)
            .border(2.dp, if (selected) colors.primary else colors.outline, CircleShape),
    ) {
        if (selected) {
            Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.size(10.dp)) {}
        }
    }
}

/** One option of a [EnclySegmentedControl]. */
data class Segment<T>(val value: T, val label: String, val enabled: Boolean = true)

/**
 * Segmented control (Mode): equal 44 dp segments in a round 1 dp `outlineVariant` frame. Selected:
 * `primaryContainer`; disabled segments show their label in `outline` and ignore taps.
 */
@Composable
fun <T> EnclySegmentedControl(
    segments: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            .padding(EnclyTheme.spacing.xxs)
            .selectableGroup(),
    ) {
        segments.forEach { segment ->
            SegmentButton(
                label = segment.label,
                selected = segment.value == selected,
                enabled = segment.enabled,
                onClick = { onSelect(segment.value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else Color.Transparent,
        label = "segment",
    )
    val content = when {
        !enabled -> colors.outline
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = EnclyTheme.spacing.textButtonHeight)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = EnclyTheme.typography.chip,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = EnclyTheme.spacing.s),
            )
        }
    }
}
