package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.presentation.components.custombox.ModalBoxItem
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.presentation.viewmodel.NoteListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSortBottomSheet(
    isBottomSheetVisible: Boolean = false,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    noteListViewModel: NoteListViewModel = hiltViewModel()
) {
    val state by noteListViewModel.state.collectAsState()

    if (isBottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = sheetState,
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.wrapContentHeight()
        ) {
            Text(
                text = "Сортувати:",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp)
            )

            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(NoteSortOption.entries.size) { index ->
                    val item = NoteSortOption.entries[index]
                    ModalBoxItem(
                        title = stringResource(item.labelRes),
                        icon = painterResource(R.drawable.ic_sort),
                        roundPosition = if (index == 0) RoundPosition.First else if (index == NoteSortOption.entries.size - 1) RoundPosition.Last else RoundPosition.Medium,
                        checked = state.noteSortOption == item,
                        action = {
                            noteListViewModel.onEvent(NoteListEvent.ToggleNoteSort(item))
                            onDismiss()
                        }
                    )
                }
            }
        }

    }

}