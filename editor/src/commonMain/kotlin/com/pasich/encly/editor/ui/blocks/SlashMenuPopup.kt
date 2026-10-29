package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.EditorStrings
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.theme.EditorIcons
import com.pasich.encly.editor.theme.EditorToolbarReserve
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.SlashActions
import com.pasich.encly.editor.ui.components.CommandMenuItem
import com.pasich.encly.editor.ui.components.EditorCommandMenu
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The slash picker of a paragraph: a small list of block kinds under the field, filtered by what
 * was typed after the "/". It never takes focus, so the keyboard stays on the paragraph and the
 * user goes on typing; Enter and the arrow keys are handled in the paragraph's key handling and
 * in [BlockInput]. It stays hidden until the "/" is committed text, so a character still under IME
 * composition never opens it.
 */
@Composable
internal fun SlashMenuPopup(field: TextFieldState, slash: SlashActions, modifier: Modifier = Modifier) {
    // Only whether this paragraph owns the menu is observed here, so the other paragraphs do not
    // recompose each time the open menu changes (a typed letter, a moved highlight). A menu opens
    // only after the "/" is typed, so starting from "not the owner" never hides an open one.
    val owns by remember(slash.menu, slash.blockId) {
        slash.menu.map { it?.blockId == slash.blockId }.distinctUntilChanged()
    }.collectAsState(initial = false)
    if (owns) OpenSlashMenu(field, slash, modifier)
}

@Composable
private fun OpenSlashMenu(field: TextFieldState, slash: SlashActions, modifier: Modifier = Modifier) {
    val all by slash.menu.collectAsState()
    val menu = all?.takeIf { it.blockId == slash.blockId }
    val labels = slashLabels()
    val strings = LocalEditorStrings.current
    val icons = LocalEditorTheme.current.icons
    val currentSlash by rememberUpdatedState(slash)

    LaunchedEffect(menu != null, labels) {
        if (menu != null) currentSlash.setLabels(labels)
    }
    val awaitingCommit = menu != null && !menu.committed
    LaunchedEffect(awaitingCommit) {
        if (awaitingCommit) {
            snapshotFlow { field.composition == null }.first { it }
            currentSlash.commit()
        }
    }
    if (menu == null || !menu.isShown) return

    val density = LocalDensity.current
    val reserve = with(density) { EditorToolbarReserve.roundToPx() }
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    val position = remember(reserve, bottomInset) { UnderOrOverField(bottomLimit = { it - bottomInset - reserve }) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { currentSlash.dismiss() },
        // Not focusable: the keyboard and the caret stay in the paragraph.
        properties = PopupProperties(focusable = false),
    ) {
        EditorCommandMenu(
            items = menu.matches.map { CommandMenuItem(it.icon(icons), labels.getValue(it)) },
            highlighted = menu.highlighted,
            title = strings.text(EditorString.SLASH_MENU_TITLE),
            onSelect = { currentSlash.select(menu.matches[it]) },
            modifier = modifier,
        )
    }
}

/** The labels built once for the whole editor (see [rememberSlashLabels]); null where nothing provides them. */
internal val LocalSlashLabels = compositionLocalOf<Map<SlashCommand, String>?> { null }

/** The localized label of every command, for the list and for matching the query. */
@Composable
private fun slashLabels(): Map<SlashCommand, String> =
    LocalSlashLabels.current ?: rememberSlashLabels(LocalEditorStrings.current)

/**
 * The label of every command in [strings]' language, built once per [strings]:
 * [com.pasich.encly.editor.EditorProvider] provides it as [LocalSlashLabels] above the editor.
 */
@Composable
internal fun rememberSlashLabels(strings: EditorStrings): Map<SlashCommand, String> = remember(strings) {
    SlashCommand.entries.associateWith { command ->
        command.headingLevel?.let(strings::heading) ?: strings.text(command.label)
    }
}

private fun SlashCommand.icon(icons: EditorIcons): ImageVector = when (this) {
    SlashCommand.TEXT -> icons.text
    SlashCommand.H1 -> icons.heading1
    SlashCommand.H2 -> icons.heading2
    SlashCommand.H3 -> icons.heading3
    SlashCommand.H4 -> icons.heading4
    SlashCommand.CHECKLIST -> icons.checklist
    SlashCommand.BULLETS -> icons.bulletList
    SlashCommand.NUMBERED -> icons.numberedList
    SlashCommand.QUOTE -> icons.quote
    SlashCommand.LINK -> icons.link
    SlashCommand.DIVIDER -> icons.separator
}

/**
 * Under the field when the list fits above [bottomLimit] (the window's height less the keyboard and
 * the editor toolbar), else over it; always inside the window's width.
 */
private class UnderOrOverField(private val bottomLimit: (windowHeight: Int) -> Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val fitsBelow = below + popupContentSize.height <= bottomLimit(windowSize.height)
        val y = if (fitsBelow) below else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
