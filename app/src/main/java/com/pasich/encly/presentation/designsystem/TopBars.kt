package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme

private const val PROGRESS_ANIMATION_MS = 250

/** The back icon button every bar uses. */
@Composable
fun EnclyBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            EnclyIcons.Back,
            contentDescription = stringResource(R.string.back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A close (×) icon button: leaves a mode (a selection) rather than the screen. */
@Composable
fun EnclyCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            EnclyIcons.Close,
            contentDescription = stringResource(R.string.close),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One entry of an [EnclyOverflowMenu]; [destructive] shows it in `error`. */
class OverflowAction(val text: String, val onClick: () -> Unit, val destructive: Boolean = false)

/**
 * The "More options" button of a bar and its menu (`surfaceContainerHigh`, radius 12, labelLarge
 * items). Nothing is drawn without [items].
 */
@Composable
fun EnclyOverflowMenu(items: List<OverflowAction>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                EnclyIcons.More,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (item.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/**
 * The top bar (design spec §3.3), 64 dp. With [onBack] it is a sub-screen bar (back, title at
 * 26 sp); without it, a root bar where [navigation] (e.g. the menu button) leads and the title is
 * headlineSmall. [actions] sit at the end. [onClose] replaces back with a close button, for a
 * mode such as a selection, and keeps the sub-screen title.
 */
@Composable
fun EnclyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigation: @Composable (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val headline = MaterialTheme.typography.headlineSmall
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .height(EnclyTheme.spacing.topBarHeight)
            .padding(horizontal = EnclyTheme.spacing.xs),
    ) {
        when {
            onClose != null -> EnclyCloseButton(onClick = onClose)
            onBack != null -> EnclyBackButton(onClick = onBack)
            navigation != null -> navigation()
        }
        Text(
            text = title,
            style = if (onBack != null || onClose != null) headline.copy(fontSize = 26.sp) else headline,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = EnclyTheme.spacing.xxs),
        )
        actions()
    }
}

/**
 * The onboarding header: back (or a 48 dp spacer), the "STEP n OF total" label and one segment
 * per step, `primary` up to [step] and `outlineVariant` after it.
 */
@Composable
fun EnclyProgressHeader(step: Int, totalSteps: Int, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .height(EnclyTheme.spacing.topBarHeight)
            .padding(start = EnclyTheme.spacing.xs, top = EnclyTheme.spacing.xs, end = EnclyTheme.spacing.m),
    ) {
        if (onBack != null) {
            EnclyBackButton(onClick = onBack)
        } else {
            Box(modifier = Modifier.size(EnclyTheme.spacing.minTouchTarget))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.onboarding_step_of, step, totalSteps).uppercase(),
                style = EnclyTheme.typography.stepLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(totalSteps) { index ->
                    ProgressSegment(done = index < step, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProgressSegment(done: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(PROGRESS_ANIMATION_MS),
        label = "progress",
    )
    Box(
        modifier = modifier
            .height(4.dp)
            .background(color, RoundedCornerShape(2.dp)),
    )
}
