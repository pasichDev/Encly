package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyRadio
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.presentation.viewmodel.NoteListViewModel

/** View options of the notes list: the sort order, and the switch between list and grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSortBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    isGrid: Boolean,
    onToggleView: () -> Unit,
    isBottomSheetVisible: Boolean = false,
    noteListViewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by noteListViewModel.state.collectAsState()

    if (isBottomSheetVisible) {
        EnclyBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            title = stringResource(R.string.sort_by),
        ) {
            NoteSortOption.entries.forEach { option ->
                EnclySheetRow(
                    title = stringResource(option.labelRes),
                    leading = { EnclyRadio(selected = state.noteSortOption == option) },
                    onClick = {
                        noteListViewModel.onEvent(NoteListEvent.ToggleNoteSort(option))
                        onDismiss()
                    },
                )
            }
            EnclyGroupDivider()
            EnclySheetRow(
                title = stringResource(if (isGrid) R.string.view_switch_to_list else R.string.view_switch_to_grid),
                icon = if (isGrid) EnclyIcons.ListView else EnclyIcons.Grid,
                onClick = {
                    onToggleView()
                    onDismiss()
                },
            )
        }
    }
}
