package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Undo2
import com.pasich.encly.R
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

enum class EditNoteBottomSheetAction {
    CLOSE_NO_SAVE,
    DUPLICATE,
    TRASH,
}

/**
 * The editor's "More actions" sheet: text size, then duplicate, discard changes and delete. The
 * font itself is chosen app-wide in Settings, Appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel<EditNoteViewModel>(),
    onAction: (EditNoteBottomSheetAction) -> Unit = {},
) {
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()

    if (isVisible) {
        EnclyBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
            FontSizeSlider(
                currentSize = fontSize,
                onSizeChange = { newSize -> viewModel.updateFontSize(newSize) },
            )
            EnclyGroupDivider()
            EnclySheetRow(
                title = stringResource(id = R.string.duplicate),
                icon = Lucide.CopyPlus,
                onClick = {
                    onAction(EditNoteBottomSheetAction.DUPLICATE)
                    onDismiss()
                },
            )
            EnclySheetRow(
                title = stringResource(R.string.note_discard_all_changes),
                icon = Lucide.Undo2,
                onClick = {
                    onAction(EditNoteBottomSheetAction.CLOSE_NO_SAVE)
                    onDismiss()
                },
            )
            EnclySheetRow(
                title = stringResource(id = R.string.delete),
                icon = Lucide.Trash2,
                destructive = true,
                confirmFirst = true,
                onClick = {
                    onAction(EditNoteBottomSheetAction.TRASH)
                    onDismiss()
                },
            )
        }
    }
}
