package com.pasich.encly.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.theme.LocalEditorTheme

/** One row of an [EditorCommandMenu]. */
data class CommandMenuItem(val icon: ImageVector, val label: String)

private val MenuMaxHeight = 240.dp
private val MenuMinWidth = 208.dp
private val MenuMaxWidth = 288.dp
private val MenuElevation = 6.dp

/**
 * A small floating list of commands (the editor's slash picker): rows at least 48 dp tall, the
 * [highlighted] one tinted so a hardware keyboard can see where Enter lands, scrolling when there
 * are more than fit. [title] describes the list to TalkBack.
 */
@Composable
fun EditorCommandMenu(
    items: List<CommandMenuItem>,
    highlighted: Int,
    title: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalEditorTheme.current.spacing
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(highlighted) {
        if (highlighted in items.indices) listState.animateScrollToItem(highlighted)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerHigh,
        shadowElevation = MenuElevation,
        modifier = modifier
            .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
            .semantics { contentDescription = title },
    ) {
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = MenuMaxHeight)) {
            itemsIndexed(items) { index, item ->
                val active = index == highlighted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) colors.primaryContainer else colors.surfaceContainerHigh)
                        .clickable(role = Role.Button) { onSelect(index) }
                        .heightIn(min = spacing.minTouchTarget)
                        .padding(horizontal = spacing.m)
                        .semantics { selected = active },
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.size(spacing.iconMedium),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) colors.onPrimaryContainer else colors.onSurface,
                    )
                }
            }
        }
    }
}
