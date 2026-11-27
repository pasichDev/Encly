package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.TextContextMenuArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.select_all
import com.pasich.encly.core.resources.selection_copy
import com.pasich.encly.core.resources.selection_cut
import com.pasich.encly.core.resources.selection_paste
import org.jetbrains.compose.resources.stringResource

/** The desktop's text fields open the menu of [EnclyTextContextMenu] on a right click. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun ProvideTextContextMenu(allowCopyAndCut: Boolean, content: @Composable () -> Unit) {
    val labels = TextMenuLabels(
        cut = stringResource(Res.string.selection_cut),
        copy = stringResource(Res.string.selection_copy),
        paste = stringResource(Res.string.selection_paste),
        selectAll = stringResource(Res.string.select_all),
    )
    val menu = remember(labels, allowCopyAndCut) { EnclyTextContextMenu(labels, allowCopyAndCut) }
    CompositionLocalProvider(LocalTextContextMenu provides menu, content = content)
}

internal actual val providesTextContextMenu: Boolean = true

/** The words of the menu rows, read from the string resources where the menu is provided. */
internal data class TextMenuLabels(val cut: String, val copy: String, val paste: String, val selectAll: String)

/**
 * The right-click menu of a text field. It is the desktop's own area (the word under the pointer is selected first,
 * the menu opens at the pointer, closes on Esc or a click outside), with rows that are ours: only what the field can
 * do right now, and Cut and Copy only when [allowCopyAndCut]. Nothing else is ever listed, so a row that a later
 * version of Compose adds does not reach a field that offers no copy.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class EnclyTextContextMenu(private val labels: TextMenuLabels, private val allowCopyAndCut: Boolean) :
    TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit,
    ) {
        TextContextMenuArea(textManager, { rows(textManager) }, state, content)
    }

    private fun rows(manager: TextContextMenu.TextManager): List<ContextMenuItem> = listOfNotNull(
        if (allowCopyAndCut) row(labels.cut, manager.cut) else null,
        if (allowCopyAndCut) row(labels.copy, manager.copy) else null,
        row(labels.paste, manager.paste),
        row(labels.selectAll, manager.selectAll),
    )

    private fun row(label: String, action: TextContextMenu.Action?): ContextMenuItem? =
        action?.takeIf { it.enabled }?.let { ContextMenuItem(label, it.execute) }
}
