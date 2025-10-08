package com.pasich.encly.presentation.components.editNote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.DynamicButtons
import com.pasich.encly.presentation.components.VerticalDivider
import com.pasich.encly.presentation.components.appbar.AppBarIconButton
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

enum class NoteBottomBarFragment {
    BLOCKS, MAIN
}



@Composable
fun NoteBottomBar(
    viewModel: EditNoteViewModel = hiltViewModel(),
    simpleEdit: Boolean = false
) {

    var noteBottomBarFragment by rememberSaveable {
        mutableStateOf(
            NoteBottomBarFragment.MAIN,
        )
    }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    val toMain = {
        noteBottomBarFragment = NoteBottomBarFragment.MAIN
    }

    Row(
        modifier = Modifier
            .imePadding()
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 0.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.MAIN) {

                AppBarIconButton(
                    icon = R.drawable.ic_undo,
                    onPressed = { viewModel.undo() },
                    tint =
                        if (canUndo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        },
                    enabled = canUndo,
                )

            }
            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.MAIN) {
                AppBarIconButton(
                    icon = R.drawable.ic_redo,
                    onPressed = { viewModel.redo() },
                    tint =
                        if (canRedo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        },
                    enabled = canRedo,
                )
            }



            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.BLOCKS) {
                AppBarIconButton(
                    icon = R.drawable.ic_close,
                    onPressed = toMain,
                )
            }

            if (!simpleEdit)
                VerticalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp,
                    height = 20.dp
                )


            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.MAIN && !simpleEdit) {
                Row {

                    AppBarIconButton(
                        icon = R.drawable.ic_add,
                        onPressed = { noteBottomBarFragment = NoteBottomBarFragment.BLOCKS },
                    )
                    AppBarIconButton(
                        icon = R.drawable.ic_up,
                        onPressed = { },
                        margin = 2.dp
                    )
                    AppBarIconButton(
                        icon = R.drawable.ic_down,
                        onPressed = { },
                        margin = 2.dp
                    )
                    AppBarIconButton(
                        icon = R.drawable.ic_trash_all_clean,
                        onPressed = { },
                        margin = 2.dp
                    )
                }
            }

            AnimatedVisibility(visible = noteBottomBarFragment == NoteBottomBarFragment.BLOCKS && !simpleEdit) {
                DynamicButtons(viewModel, toMain)
            }
        }
    }
}

