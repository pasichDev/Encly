package com.pasich.encly.editor.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.state.SelectedBlocks
import com.pasich.encly.editor.state.TextRangeSelection
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.components.EditorTooltip
import com.pasich.encly.editor.ui.components.editorInteractive

private val ButtonSize = 44.dp
private val ButtonShape = RoundedCornerShape(14.dp)
private const val DISABLED_ALPHA = 0.38f

/**
 * The bar shown over the note while whole blocks are selected: leave the mode, how many blocks are picked (read
 * out by TalkBack as it changes), then Copy, Copy as Markdown, Cut and Delete. It holds the keyboard focus while
 * it shows, so a hardware keyboard works on the blocks: Shift+Up and Down extend the selection, Escape leaves,
 * Ctrl or Cmd+A selects everything, +C copies (+Shift as Markdown), +X cuts, and Delete removes.
 */
@Composable
public fun SelectedBlocksBar(selected: SelectedBlocks, actions: SelectedBlocksActions, modifier: Modifier = Modifier) {
    val count = selected.ids.collectAsState().value.size
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Fields cannot type into picked blocks: the keyboard goes, and the bar takes the keys.
    LaunchedEffect(focus) {
        keyboard?.hide()
        runCatching { focus.requestFocus() }
    }
    SelectionBar(
        count = count,
        onClose = selected::clear,
        actions = actions,
        modifier = modifier
            .onKeyEvent { event -> event.selectionKey()?.perform(selected, actions) != null }
            .focusRequester(focus)
            .focusable(),
    )
}

/**
 * The bar shown over the note while a text selection runs across blocks: leave it, how many blocks it touches (read
 * out by TalkBack as it changes), then Copy, Copy as Markdown, Cut and Delete. The fields keep the keyboard focus, so
 * typing, Delete, Ctrl+C and the other keys act on the selection from where they were pressed.
 */
@Composable
public fun TextRangeBar(range: TextRangeSelection, actions: SelectedBlocksActions, modifier: Modifier = Modifier) {
    val active = range.ends.collectAsState().value != null
    if (active) SelectionBar(range.blockCount(), range::clear, actions, modifier)
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    actions: SelectedBlocksActions,
    modifier: Modifier = Modifier,
) {
    val strings = LocalEditorStrings.current
    val icons = LocalEditorTheme.current.icons
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LocalEditorTheme.current.spacing.xxs),
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BarButton(icons.close, strings.text(EditorString.SELECTION_CLOSE), onClose)
                Text(
                    text = strings.selectionCount(count),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = LocalEditorTheme.current.spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                BarButton(icons.copy, strings.text(EditorString.SELECTION_COPY), actions.onCopy)
                BarButton(null, strings.text(EditorString.SELECTION_COPY_MARKDOWN), actions.onCopyMarkdown, "MD")
                BarButton(icons.cut, strings.text(EditorString.SELECTION_CUT), actions.onCut.takeIf { actions.canEdit })
                BarButton(
                    icons.trash,
                    strings.text(EditorString.SELECTION_DELETE),
                    actions.onDelete.takeIf { actions.canEdit },
                )
            }
        }
    }
}

/** A 44 dp button of the bar: [icon], or the short [text] when it has none; a null [onClick] shows it disabled. */
@Composable
private fun BarButton(icon: ImageVector?, label: String, onClick: (() -> Unit)?, text: String? = null) {
    val enabled = onClick != null
    val content = MaterialTheme.colorScheme.onSurface
    EditorTooltip(label) {
        Surface(
            onClick = { onClick?.invoke() },
            enabled = enabled,
            shape = ButtonShape,
            color = Color.Transparent,
            contentColor = if (enabled) content else content.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier
                .editorInteractive(ButtonShape, enabled)
                .size(ButtonSize)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    val size = LocalEditorTheme.current.spacing.iconMedium
                    Icon(icon, contentDescription = null, modifier = Modifier.size(size))
                } else {
                    Text(text.orEmpty(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
