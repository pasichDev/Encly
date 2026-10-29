package com.pasich.encly.editor.ui.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.theme.LocalEditorTheme
import kotlinx.coroutines.delay

/** How long a destructive row waits for the confirming second tap. */
private const val CONFIRM_WINDOW_MS = 5_000L
private const val TITLE_MAX_LINES = 3
private const val DISABLED_ALPHA = 0.38f
private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleRadius = 2.dp

/**
 * The editor's bottom sheet: top corners 16, `surfaceContainerLow`, a 32×4 handle in
 * `outlineVariant` and a gutter. [title] is shown in titleLarge above [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalEditorTheme.current.spacing
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = { SheetHandle() },
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = spacing.gutter, end = spacing.gutter, bottom = spacing.l),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = spacing.xs),
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
            .padding(vertical = LocalEditorTheme.current.spacing.s)
            .size(width = HandleWidth, height = HandleHeight)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(HandleRadius)),
    )
}

/** A hairline between groups of sheet rows. */
@Composable
internal fun EditorGroupDivider(modifier: Modifier = Modifier) {
    val spacing = LocalEditorTheme.current.spacing
    HorizontalDivider(
        thickness = spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.padding(horizontal = spacing.m),
    )
}

/**
 * A sheet action row: leading icon in `primary` (`error` when [destructive]) and the title in
 * labelLarge. With [confirmFirst] the first tap only arms the action ("Tap again to delete"); a
 * second tap within five seconds runs it.
 */
@Composable
internal fun EditorSheetRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    confirmFirst: Boolean = false,
) {
    val spacing = LocalEditorTheme.current.spacing
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
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.rowHeight)
            .clickable(enabled = enabled, role = Role.Button) {
                if (confirmFirst && !armed) {
                    armed = true
                } else {
                    armed = false
                    onClick()
                }
            }
            .padding(horizontal = spacing.xxs, vertical = spacing.xs),
    ) {
        if (armed) {
            CircularProgressIndicator(
                color = accent,
                strokeWidth = spacing.stroke,
                modifier = Modifier.size(spacing.icon),
            )
        } else {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(spacing.icon))
        }
        Text(
            text = if (armed) LocalEditorStrings.current.text(EditorString.TAP_AGAIN_TO_DELETE) else title,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Icon and title colours of a sheet row: `primary` / `onSurface`, `error` when destructive. */
@Composable
private fun sheetRowColors(enabled: Boolean, destructive: Boolean): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val disabled = colors.onSurface.copy(alpha = DISABLED_ALPHA)
    return when {
        !enabled -> disabled to disabled
        destructive -> colors.error to colors.error
        else -> colors.primary to colors.onSurface
    }
}
