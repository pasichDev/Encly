package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasich.encly.R
import com.pasich.encly.presentation.effects.shimmerEffect
import com.pasich.encly.ui.theme.EnclyTheme

private const val HOLD_TO_CONFIRM_MS = 4_000
private const val HALF = 0.5f
private const val CHEVRON_TURN = 180f
private const val SKELETON_TITLE_FRACTION = 0.6f
private const val SKELETON_LINE_FRACTION = 0.9f

/** Height of a note-card skeleton (title, two lines, meta). */
private val SkeletonCardHeight = 112.dp
private val SkeletonLineHeight = 14.dp

/**
 * Loading placeholder of a note card: the card shape shimmering between `surfaceContainer` and
 * `surfaceContainerHigh`.
 */
@Composable
fun NoteSkeleton(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkeletonCardHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(EnclyTheme.spacing.m),
    ) {
        SkeletonLine(SKELETON_TITLE_FRACTION)
        SkeletonLine(1f)
        SkeletonLine(SKELETON_LINE_FRACTION)
    }
}

/** Loading placeholder of a list row (a task, a tag): one shimmering line at the row height. */
@Composable
fun RowSkeleton(modifier: Modifier = Modifier, fraction: Float = SKELETON_TITLE_FRACTION) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.textButtonHeight),
    ) {
        SkeletonLine(fraction)
    }
}

@Composable
private fun SkeletonLine(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(SkeletonLineHeight)
            .clip(MaterialTheme.shapes.extraSmall)
            .shimmerEffect(),
    )
}

/**
 * A destructive action that only runs after the button is held for four seconds; the fill grows
 * while it is held and resets when released.
 */
@Composable
fun EnclyHoldToConfirmButton(text: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(durationMillis = if (holding) HOLD_TO_CONFIRM_MS else 0),
        label = "hold",
        finishedListener = { if (it >= 1f) onConfirm() },
    )
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(EnclyTheme.spacing.buttonHeight)
            .clip(CircleShape)
            .background(colors.errorContainer)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(colors.error),
        )
        Text(
            text = text,
            style = EnclyTheme.typography.buttonPrimary,
            color = if (progress > HALF) colors.onError else colors.onErrorContainer,
        )
    }
}

/**
 * An expandable question (FAQ): the question in labelLarge and a chevron that turns 180° when the
 * answer (bodyMedium, muted) is shown.
 */
@Composable
fun EnclyExpandableRow(question: String, answer: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) CHEVRON_TURN else 0f, label = "chevron")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = EnclyTheme.spacing.m, vertical = EnclyTheme.spacing.rowGap),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                EnclyIcons.ChevronDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(EnclyTheme.spacing.iconSmall)
                    .rotate(turn),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val SwatchShape = RoundedCornerShape(14.dp)
private val SwatchAccentShape = RoundedCornerShape(3.dp)
private val SwatchCardShape = RoundedCornerShape(5.dp)
private val SwatchDotShape = RoundedCornerShape(6.dp)
private const val SWATCH_ACCENT_FRACTION = 0.6f
private val SWATCH_LABEL_MIN = 9.sp
private val SWATCH_LABEL_MAX = 13.sp

/**
 * A theme swatch (Appearance): an 88 dp miniature of [preview] (its surface, an accent bar, two
 * card bars and an accent square), ringed in the current `primary` when [selected], and [label].
 */
@Composable
fun ThemeSwatch(
    preview: ColorScheme,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .border(if (selected) EnclyTheme.spacing.stroke else EnclyTheme.spacing.hairline, ring, SwatchShape)
                .background(preview.surface, SwatchShape)
                .padding(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(SWATCH_ACCENT_FRACTION)
                    .height(6.dp)
                    .background(preview.primary, SwatchAccentShape),
            )
            repeat(2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EnclyTheme.spacing.iconXSmall)
                        .background(preview.surfaceContainer, SwatchCardShape),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.BottomEnd) {
                Box(modifier = Modifier.size(EnclyTheme.spacing.m).background(preview.primary, SwatchDotShape))
            }
        }
        // Long translated names shrink to fit the narrow column instead of being cut.
        BasicText(
            text = label,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = SWATCH_LABEL_MIN, maxFontSize = SWATCH_LABEL_MAX),
            style = EnclyTheme.typography.swatchLabel.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

/** Snackbars (design spec §4.6): `inverseSurface` / `inverseOnSurface`, radius 12, action in `inversePrimary`. */
@Composable
fun EnclySnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/**
 * A drawer destination: 56 dp, fully round, labelLarge; selected in `primaryContainer`. [badge]
 * (a count) is shown at the end in the data style.
 */
@Composable
fun EnclyDrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: Int? = null,
) {
    NavigationDrawerItem(
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(EnclyTheme.spacing.icon)) },
        badge = badge?.let { count -> { Text(text = count.toString(), style = EnclyTheme.typography.dataSmall) } },
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedBadgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .heightIn(min = EnclyTheme.spacing.rowHeight)
            .padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

/** An inline text link ("All tasks"): labelMedium in `primary`. */
@Composable
fun EnclyInlineLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = EnclyTheme.spacing.xs, vertical = EnclyTheme.spacing.xxs),
    )
}
