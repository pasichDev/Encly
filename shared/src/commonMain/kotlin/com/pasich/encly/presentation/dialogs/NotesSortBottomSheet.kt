package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.sort_by
import com.pasich.encly.core.resources.view_switch_to_grid
import com.pasich.encly.core.resources.view_switch_to_list
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyRadio
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/** View options of the notes list: the sort order, and the switch between list and grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSortBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    isGrid: Boolean,
    onToggleView: () -> Unit,
    isBottomSheetVisible: Boolean = false,
    noteListViewModel: NoteListViewModel = metroViewModel(),
) {
    val state by noteListViewModel.state.collectAsState()

    if (isBottomSheetVisible) {
        AdaptiveSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            title = stringResource(Res.string.sort_by),
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
                title = stringResource(if (isGrid) Res.string.view_switch_to_list else Res.string.view_switch_to_grid),
                icon = if (isGrid) EnclyIcons.ListView else EnclyIcons.Grid,
                onClick = {
                    onToggleView()
                    onDismiss()
                },
            )
        }
    }
}
