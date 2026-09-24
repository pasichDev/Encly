package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.pasich.encly.ui.theme.EnclyTheme

/** Card styles (design spec §3.3). Hierarchy comes from tonal surfaces, never from shadows. */
enum class EnclyCardStyle { TONAL, OUTLINED }

/**
 * A card: radius 16, padding 16. [EnclyCardStyle.TONAL] fills `surfaceContainer`;
 * [EnclyCardStyle.OUTLINED] draws 1 dp `outlineVariant` on the screen surface.
 */
@Composable
fun EnclyCard(
    modifier: Modifier = Modifier,
    style: EnclyCardStyle = EnclyCardStyle.TONAL,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(EnclyTheme.spacing.m),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (style == EnclyCardStyle.TONAL) colors.surfaceContainer else Color.Transparent
    val border = if (style == EnclyCardStyle.OUTLINED) BorderStroke(1.dp, colors.outlineVariant) else null
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = colors.onSurface,
            border = border,
            modifier = modifier.fillMaxWidth(),
            content = inner,
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = colors.onSurface,
            border = border,
            modifier = modifier.fillMaxWidth(),
            content = inner,
        )
    }
}

/** Callout tone. Warnings use neutral surfaces, not error colours (design spec §3.3). */
enum class CalloutTone { INFO, WARNING }

/** An explanatory box with a leading icon. */
@Composable
fun EnclyCallout(
    text: String,
    modifier: Modifier = Modifier,
    tone: CalloutTone = CalloutTone.INFO,
    icon: ImageVector = if (tone == CalloutTone.INFO) Lucide.Info else Lucide.TriangleAlert,
    title: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val info = tone == CalloutTone.INFO
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (info) colors.surfaceContainer else colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (info) EnclyTheme.spacing.m else EnclyTheme.spacing.s),
            modifier = Modifier.padding(
                horizontal = EnclyTheme.spacing.m,
                vertical = if (info) EnclyTheme.spacing.s else EnclyTheme.spacing.rowGap,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (info) colors.primary else colors.onSurface,
                modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap),
            ) {
                if (title != null) Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A filter chip: 36 dp, fully round. Selected: `primaryContainer`; unselected: transparent with a
 * 1 dp `outlineVariant` border. [count] is shown after the label in the data style.
 */
@Composable
fun EnclyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Surface(
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else Color.Transparent,
        contentColor = content,
        border = BorderStroke(1.dp, if (selected) colors.primaryContainer else colors.outlineVariant),
        modifier = modifier
            .heightIn(min = 36.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = EnclyTheme.spacing.xs),
        ) {
            Text(text = label, style = EnclyTheme.typography.chip)
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = EnclyTheme.typography.dataSmall,
                    color = content.copy(alpha = COUNT_ALPHA),
                )
            }
        }
    }
}

private const val COUNT_ALPHA = 0.85f

/** A section heading: the uppercase data overline in `onSurfaceVariant`. */
@Composable
fun SectionOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A grouped list: rows on one `surfaceContainer` block with radius 16. */
@Composable
fun EnclyGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = EnclyTheme.spacing.xxs), content = content)
    }
}

private val GroupRadius = 16.dp

/**
 * The shape of row [index] of [count] in a grouped list drawn row by row (a lazy list): the
 * first row carries the top corners, the last the bottom ones.
 */
fun groupRowShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) GroupRadius else 0.dp,
    topEnd = if (index == 0) GroupRadius else 0.dp,
    bottomStart = if (index == count - 1) GroupRadius else 0.dp,
    bottomEnd = if (index == count - 1) GroupRadius else 0.dp,
)
