package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.editor.ui.sheets.EditorBlockSheet
import com.pasich.encly.presentation.components.editNote.NoteTagSheet
import com.pasich.encly.presentation.designsystem.EnclyBackButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTooltip
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The sheet of one block (a link's, or a separator's); the editor shows it (`EditorBlockSheet`),
 * the app only says which one is open and whether it may change the note.
 */
@Composable
internal fun EditorBlockSheets(sheet: BlockSheetState, canEdit: Boolean) {
    val controller = rememberEditorController()
    EditorBlockSheet(
        kind = sheet.kind,
        blockId = sheet.blockId,
        canEdit = canEdit,
        controller = controller,
        onClose = sheet::close,
    )
}

/** Chooses the note's tag; "Manage tags" leaves for the tags screen ([onManageTags]). */
@Composable
internal fun EditorTagSheet(
    onManageTags: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: EditNoteViewModel = metroViewModel(),
    tagViewModel: TagListViewModel = metroViewModel(),
) {
    val tagState by tagViewModel.state.collectAsState()
    val noteState by viewModel.state.collectAsState()
    NoteTagSheet(
        tags = tagState.listTags,
        selectedTagId = noteState.note.tagId,
        onSelect = { tagId ->
            viewModel.updateTagNote(tagId)
            onDismiss()
        },
        onManageTags = onManageTags,
        onDismiss = onDismiss,
    )
}

/** What the editor bar shows: trash actions, or tags, lock (unless the note is unreadable) and more. */
internal class EditorBarState(
    val isReadTrashOnly: Boolean,
    val lockEditor: Boolean,
    val canToggleLock: Boolean,
    val containerColor: Color,
)

/**
 * The editor bar: back (saves and closes), then Edit tags, Lock editing and More actions. In
 * the trash: back, Restore and Delete.
 */
internal class EditorBarActions(
    val onBack: () -> Unit,
    val onRestore: () -> Unit,
    val onDelete: () -> Unit,
    val onTags: () -> Unit,
    val onMenu: () -> Unit,
    val onLockToggle: () -> Unit,
)

/** Pinned above the note; a hairline appears once the note scrolls under it. */
@Composable
internal fun EditorTopBar(
    state: EditorBarState,
    listState: LazyListState,
    actions: EditorBarActions,
    modifier: Modifier = Modifier,
) {
    val scrolled by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val colors = MaterialTheme.colorScheme
    Surface(color = state.containerColor, modifier = modifier) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EnclyTheme.spacing.topBarHeight)
                    .padding(horizontal = EnclyTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            ) {
                EnclyBackButton(onClick = actions.onBack)
                Spacer(modifier = Modifier.weight(1f))
                if (state.isReadTrashOnly) {
                    EnclyTextButton(text = stringResource(R.string.restore), onClick = actions.onRestore)
                    EnclyTextButton(
                        text = stringResource(R.string.delete_from_trash),
                        onClick = actions.onDelete,
                        destructive = true,
                    )
                } else {
                    EditorBarButtons(state, actions)
                }
            }
            HorizontalDivider(
                thickness = EnclyTheme.spacing.hairline,
                color = if (scrolled) colors.outlineVariant else state.containerColor,
            )
        }
    }
}

@Composable
private fun EditorBarButtons(state: EditorBarState, actions: EditorBarActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs)) {
        if (state.canToggleLock) {
            val tagsLabel = stringResource(R.string.edit_tags)
            EnclyTooltip(tagsLabel) {
                IconButton(
                    onClick = actions.onTags,
                    enabled = !state.lockEditor,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurface),
                ) {
                    Icon(EnclyIcons.Tag, contentDescription = tagsLabel)
                }
            }
            // A toggle, so TalkBack announces whether editing is locked.
            val lockLabel = stringResource(R.string.editor_lock)
            EnclyTooltip(lockLabel) {
                IconToggleButton(
                    checked = state.lockEditor,
                    onCheckedChange = { actions.onLockToggle() },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        contentColor = colors.onSurface,
                        checkedContentColor = colors.primary,
                    ),
                ) {
                    Icon(
                        if (state.lockEditor) EnclyIcons.Lock else EnclyIcons.LockOpen,
                        contentDescription = lockLabel,
                    )
                }
            }
        }
        IconWithTooltip(EnclyIcons.More, stringResource(R.string.more_options), actions.onMenu, tint = colors.onSurface)
    }
}
