package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.SheetShape
import kotlinx.coroutines.delay

/** How long a destructive row waits for the confirming second tap. */
private const val CONFIRM_WINDOW_MS = 5_000L
private const val TITLE_MAX_LINES = 3

/** Elevation of the FAB and sheet shadow (design spec §1.4). */
internal val ShadowElevation = 10.dp

/**
 * A bottom sheet (design spec §3.3): top corners 16, `surfaceContainerLow`, no tonal elevation,
 * the shadow token, a 32×4 handle in `outlineVariant` and a 24 dp gutter. [title] is shown in
 * titleLarge above [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnclyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    showHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = if (showHandle) {
            { SheetHandle() }
        } else {
            null
        },
        // No Modifier.shadow here: its clipping graphics layer sits outside the sheet's offset
        // animation and cut the whole content away, leaving an empty sheet.
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = EnclyTheme.spacing.gutter,
                    end = EnclyTheme.spacing.gutter,
                    top = if (showHandle) 0.dp else EnclyTheme.spacing.l,
                    bottom = EnclyTheme.spacing.l,
                ),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = EnclyTheme.spacing.xs),
                )
            }
            content()
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = EnclyTheme.spacing.s)
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
    )
}

/**
 * A sheet action row: leading icon in `primary` (`error` when [destructive]), the title in
 * labelLarge, and a check at the end when [selected]. With [confirmFirst] the first tap only
 * arms the action ("Tap again to delete"); a second tap within five seconds runs it. A [muted]
 * title reads as a placeholder; titles stop at three lines. [leading] (e.g. a radio) takes the
 * icon's place when there is no [icon].
 */
@Composable
fun EnclySheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    confirmFirst: Boolean = false,
    muted: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CONFIRM_WINDOW_MS)
            armed = false
        }
    }
    val (accent, titleColor) = sheetRowColors(enabled = enabled, destructive = destructive)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.rowHeight)
            .clickable(enabled = enabled, role = Role.Button) {
                if (confirmFirst && !armed) {
                    armed = true
                } else {
                    armed = false
                    onClick()
                }
            }
            .padding(horizontal = EnclyTheme.spacing.xxs, vertical = EnclyTheme.spacing.xs),
    ) {
        if (icon == null && leading != null && !armed) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(EnclyTheme.spacing.icon)) { leading() }
        } else {
            SheetRowLeading(armed = armed, icon = icon, tint = accent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap)) {
            Text(
                text = if (armed) stringResource(R.string.tap_again_to_delete) else title,
                style = MaterialTheme.typography.labelLarge,
                color = if (muted) colors.onSurfaceVariant else titleColor,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(
                EnclyIcons.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
    }
}

/** The row's icon, or a spinner while a destructive row waits for its second tap. */
@Composable
private fun SheetRowLeading(armed: Boolean, icon: ImageVector?, tint: Color) {
    if (armed) {
        CircularProgressIndicator(
            color = tint,
            strokeWidth = EnclyTheme.spacing.stroke,
            modifier = Modifier.size(EnclyTheme.spacing.icon),
        )
    } else if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(EnclyTheme.spacing.icon))
    }
}

/** Icon and title colours of a sheet row: `primary` / `onSurface`, `error` when destructive. */
@Composable
private fun sheetRowColors(enabled: Boolean, destructive: Boolean): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when {
        !enabled -> colors.onSurface.disabled() to colors.onSurface.disabled()
        destructive -> colors.error to colors.error
        else -> colors.primary to colors.onSurface
    }
}
