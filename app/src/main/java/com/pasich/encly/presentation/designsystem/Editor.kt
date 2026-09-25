package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** Editor tool buttons: 44 dp, radius 14 (design spec §3.3). */
private val ToolSize = 44.dp
private val ToolShape = RoundedCornerShape(14.dp)

/** Outline of the block being edited: 2 dp `primary`, radius 12, bleeding 8 dp into the gutter. */
private val BlockOutlineShape = RoundedCornerShape(12.dp)

/**
 * The editor's formatting toolbar, pinned above the keyboard: `surfaceContainerHigh`, a 1 dp
 * `outlineVariant` hairline on top, padding 8/10 with 16 below (plus the navigation bar).
 */
@Composable
fun EnclyEditorToolbar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(thickness = EnclyTheme.spacing.hairline, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

/** How a [EnclyToolButton] is filled. */
enum class ToolStyle { PLAIN, ACTIVE, FILLED }

/**
 * A 44 dp toolbar button with radius 14 and a 22 dp icon. [ToolStyle.FILLED] is the `primary`
 * "Add block"; [ToolStyle.ACTIVE] marks the tool of the focused block (`primaryContainer`).
 * A null [onClick] shows it disabled.
 */
@Composable
fun EnclyToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: ToolStyle = ToolStyle.PLAIN,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = onClick != null
    val (container, content) = when (style) {
        ToolStyle.FILLED -> colors.primary to colors.onPrimary
        ToolStyle.ACTIVE -> colors.primaryContainer to colors.onPrimaryContainer
        ToolStyle.PLAIN -> Color.Transparent to colors.onSurface
    }
    Surface(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        shape = ToolShape,
        color = container,
        contentColor = if (enabled) content else content.disabled(),
        modifier = modifier
            .size(ToolSize)
            .semantics {
                role = Role.Button
                selected = style == ToolStyle.ACTIVE
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(EnclyTheme.spacing.iconMedium))
        }
    }
}

/** Padding inside a block frame; the editor column is inset by [BlockBleed] less than the gutter. */
private val BlockBleed = 8.dp
private val BlockInset = 4.dp

/**
 * The frame of one editor block: inner padding 4/8 and, while [active], a 2 dp outline in [color]
 * with radius 12. The editor column sits [BlockBleed] inside the gutter, so text lines up with
 * the title and the outline bleeds into the gutter (design spec §4.4).
 */
fun Modifier.editorBlockFrame(active: Boolean, color: Color): Modifier =
    (if (active) border(2.dp, color, BlockOutlineShape) else this)
        .padding(horizontal = BlockBleed, vertical = BlockInset)

/** A thin vertical rule between groups of toolbar buttons. */
@Composable
fun EnclyToolbarRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = EnclyTheme.spacing.hairline, height = EnclyTheme.spacing.iconSmall)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
