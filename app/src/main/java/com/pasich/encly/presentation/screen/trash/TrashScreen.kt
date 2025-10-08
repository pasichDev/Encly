package com.pasich.encly.presentation.screen.trash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashViewModel

enum class DialogTrashAction {
    DELETE_PERMANENTLY, CLEAN_ALL, DISABLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    navController: NavHostController, trashViewModel: TrashViewModel = hiltViewModel()
) {

    val state by trashViewModel.state.collectAsStateWithLifecycle()
    var isDialogVisible by remember { mutableStateOf<DialogTrashAction>(DialogTrashAction.DISABLE) }

    ConfirmDialog(
        isVisible = isDialogVisible != DialogTrashAction.DISABLE,
        titleText = if (isDialogVisible == DialogTrashAction.CLEAN_ALL) stringResource(R.string.clean_trash_title) else pluralStringResource(
            R.plurals.delete_notes_confirm, state.checkedCount, state.checkedCount
        ),
        messageText = if (isDialogVisible == DialogTrashAction.CLEAN_ALL) stringResource(R.string.clean_trash_message) else stringResource(
            R.string.clean_permanent_message
        ),
        onConfirm = {
            when (isDialogVisible) {
                DialogTrashAction.CLEAN_ALL -> trashViewModel.onEvent(TrashListEvent.CleanAll())
                DialogTrashAction.DELETE_PERMANENTLY -> trashViewModel.onEvent(
                    TrashListEvent.CleanNotes()
                )

                DialogTrashAction.DISABLE -> {}
            }
            isDialogVisible = DialogTrashAction.DISABLE

        },
        onDismiss = {
            isDialogVisible = DialogTrashAction.DISABLE
        })


    Scaffold(
        topBar = {
            TopAppBar(title = {
                if (state.canCheck) Text(
                    stringResource(
                        id = R.string.checked_count, state.checkedCount
                    )
                )
                else Text(stringResource(R.string.main_drawer_trash))
            }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, actions = {


                AnimatedVisibility(state.canCheck) {
                    IconButton(onClick = { trashViewModel.onEvent(TrashListEvent.RestoreNotes()) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_trash_restore),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (state.notes.isEmpty()) IconButton(onClick = {}, enabled = false) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash_empty),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else IconButton(onClick = {
                    isDialogVisible =
                        if (state.canCheck) DialogTrashAction.DELETE_PERMANENTLY else DialogTrashAction.CLEAN_ALL
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash_all_clean),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            })
        }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TrashNotesList(onItemClick = { index, note ->
                navController.navigate("${NavRoutes.EditNoteRoute.name}/${note.id}?isReadTrashOnly=true")
            }, trashViewModel)
        }
    }
}