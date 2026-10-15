package com.pasich.encly.presentation.editor.blocks

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.CommandMenuItem
import com.pasich.encly.presentation.designsystem.EnclyCommandMenu
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyToolbarReserve
import com.pasich.encly.presentation.editor.SlashActions
import com.pasich.encly.presentation.editor.state.SlashCommand
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
    val reserve = with(density) { EnclyToolbarReserve.roundToPx() }
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    val position = remember(reserve, bottomInset) { UnderOrOverField(bottomLimit = { it - bottomInset - reserve }) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { currentSlash.dismiss() },
        // Not focusable: the keyboard and the caret stay in the paragraph.
        properties = PopupProperties(focusable = false),
    ) {
        EnclyCommandMenu(
            items = menu.matches.map { CommandMenuItem(it.icon(), labels.getValue(it)) },
            highlighted = menu.highlighted,
            title = stringResource(R.string.slash_menu_title),
            onSelect = { currentSlash.select(menu.matches[it]) },
            modifier = modifier,
        )
    }
}

/** The labels built once for the whole editor (see [rememberSlashLabels]); null where nothing provides them. */
internal val LocalSlashLabels = compositionLocalOf<Map<SlashCommand, String>?> { null }

/** The localized label of every command, for the list and for matching the query. */
@Composable
private fun slashLabels(): Map<SlashCommand, String> = LocalSlashLabels.current ?: rememberSlashLabels()

/** The localized label of every command, built once per locale: provide it as [LocalSlashLabels] above the editor. */
@Composable
internal fun rememberSlashLabels(): Map<SlashCommand, String> {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) {
        SlashCommand.entries.associateWith { command ->
            command.headingLevel?.let { resources.getString(command.label, it) } ?: resources.getString(command.label)
        }
    }
}

private fun SlashCommand.icon(): ImageVector = when (this) {
    SlashCommand.TEXT -> EnclyIcons.Type
    SlashCommand.H1 -> EnclyIcons.Heading1
    SlashCommand.H2 -> EnclyIcons.Heading2
    SlashCommand.H3 -> EnclyIcons.Heading3
    SlashCommand.H4 -> EnclyIcons.Heading4
    SlashCommand.CHECKLIST -> EnclyIcons.Checklist
    SlashCommand.BULLETS -> EnclyIcons.ListBullet
    SlashCommand.NUMBERED -> EnclyIcons.ListNumbered
    SlashCommand.QUOTE -> EnclyIcons.Quote
    SlashCommand.LINK -> EnclyIcons.Link
    SlashCommand.DIVIDER -> EnclyIcons.Separator
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
