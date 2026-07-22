package com.pasich.encly.presentation.screen.editnote

import android.widget.Toast
import com.pasich.encly.core.AppLogger
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.dynamicBlocks.DynamicBlocksEditor
import com.pasich.encly.presentation.components.appbar.AppBarIconButton
import com.pasich.encly.presentation.components.appbar.AppBarTextButton
import com.pasich.encly.presentation.components.editNote.NoteBottomBar
import com.pasich.encly.presentation.components.editNote.NoteSubTitle
import com.pasich.encly.presentation.components.editNote.TitleField
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheet
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheetAction
import com.pasich.encly.presentation.dialogs.blocks.ActionBlockDialog
import com.pasich.encly.presentation.dialogs.blocks.ActionLinkBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.ActionOtherBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.SettingsBlockDialog
import com.pasich.encly.presentation.effects.NoteSkeleton
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.SaveStatusNote
import com.pasich.encly.utils.NotesTextFormatter
import com.pasich.encly.utils.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun EditNoteScreen(
    navController: NavHostController,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
    val saveStatus by viewModel.status.collectAsState()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val fontStyle by viewModel.fontStyle.collectAsStateWithLifecycle()
    val simpleEdit by viewModel.simpleEdit.collectAsStateWithLifecycle()
    val lockEditor by viewModel.lockEditor.collectAsState()
    var isDialogVisible by remember { mutableStateOf(false) }
    val noteState by viewModel.state.collectAsState()
    val lastInteractionIndex by viewModel.lastInteractionIndex.collectAsState()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()

    // Warn the user when stored content could not be read, instead of showing a silent
    // blank editor. Editing/saving is already guarded so the unreadable data is preserved.
    LaunchedEffect(contentLoadFailed) {
        if (contentLoadFailed) {
            Toast.makeText(
                currentContext,
                currentContext.getString(R.string.note_load_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    var bottomSheetsType by rememberSaveable {
        mutableStateOf(
            BottomSheetsOpenType.NONE,
        )
    }
    var isEditMenuBottomSheetVisible by rememberSaveable { mutableStateOf(false) }

    // Function for correctly closing the screen
    val closeScreen = {
        scope.launch {
            // Hide the keyboard
            keyboardController?.hide()
            // Clear focus
            focusManager.clearFocus()
            // Small delay to let animations finish
            delay(100)
            // Close the screen
            navController.popBackStack()
        }
    }
    val imeVisible = WindowInsets.isImeVisible

    // Handling the system "Back" button
    BackHandler {
        closeScreen()
    }

    ConfirmDialog(
        isVisible = isDialogVisible,
        titleText = stringResource(R.string.dialog_title),
        messageText = stringResource(R.string.dialog_message),
        onConfirm = {
            scope.launch {
                viewModel.noteDelete()
                isDialogVisible = false
                closeScreen()
            }
        },
        onDismiss = {
            isDialogVisible = false
        },
    )

    ActionOtherBottomSheet(
        settings =
            SettingsBlockDialog(
                block = viewModel.blocks[lastInteractionIndex],
                isBottomSheetVisible = BottomSheetsOpenType.ACTION_OTHER == bottomSheetsType,
                blockMove = viewModel.getMoveBlockState(),
            ),
        sheetState = sheetState,
        onDismiss = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                bottomSheetsType = BottomSheetsOpenType.NONE
            }
        },
        onAction = { action ->
            when (action) {
                ActionBlockDialog.Delete -> {
                    viewModel.blocks[lastInteractionIndex].let {
                        viewModel.removeBlock(
                            it,
                            BlockRemoveAction.REMOVE,
                        )
                    }
                }

                is ActionBlockDialog.Move -> {
                    viewModel.moveBlock(action.move == 1)
                }

            }
        },
    )

    ActionLinkBottomSheet(
        sheetState = sheetState,
        settings =
            SettingsBlockDialog(
                block = viewModel.blocks[lastInteractionIndex],
                isBottomSheetVisible = BottomSheetsOpenType.ACTION_LINK == bottomSheetsType,
            ),
        onDismiss = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                bottomSheetsType = BottomSheetsOpenType.NONE
            }
        },
        onAction = {
            when (it) {
                ActionBlockDialog.Delete -> {
                    viewModel.blocks[lastInteractionIndex].let { it1 ->
                        viewModel.removeBlock(
                            it1,
                            BlockRemoveAction.REMOVE,
                        )
                    }
                }

                is ActionBlockDialog.Move -> {
                    viewModel.moveBlock(it.move == 1)
                }
            }
        },
    )



    EditNoteSettingsProvider(
        baseFontSize = fontSize.sp,
        fontStyle = fontStyle,
        simpleEdit = simpleEdit,
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.ime,
            bottomBar = {
                if (imeVisible && !lockEditor && !viewModel.isReadTrashOnly) {
                    NoteBottomBar(
                        viewModel = viewModel,
                        simpleEdit = LocalSimpleEdit.current,
                    )
                }
            },
        ) { padding ->
            CompositionLocalProvider(
                LocalOverscrollFactory provides null,
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .statusBarsPadding(),
                ) {
                    item {
                        TopBarContent(
                            isReadTrashOnly = viewModel.isReadTrashOnly,
                            lockEditor = lockEditor,
                            onBackClick = { closeScreen() },
                            onRestoreClick = {
                                scope.launch {
                                    viewModel.noteRestore()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    delay(100)
                                    navController.popBackStack()
                                }
                            },
                            onDeleteClick = { isDialogVisible = true },
                            onMenuClick = { isEditMenuBottomSheetVisible = true },
                            onLockToggle = { viewModel.toggleLockEditor() },
                            onDoneClick = {
                                scope.launch {
                                    viewModel.saveNote(actionButton = true)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    delay(100)
                                    navController.popBackStack()
                                }
                            },
                        )
                    }

                    // NoteSubTitle
                    if (!lockEditor && !viewModel.isReadTrashOnly) {
                        item {
                            NoteSubTitle(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                statusSaveNote = saveStatus,
                                note = noteState.note,
                                changeTag = { viewModel.updateTagNote(it) },
                                isDuplicate = viewModel.copySource != -1L,
                            )
                        }
                    }

                    // Skeleton or content
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(
                                visible = saveStatus == SaveStatusNote.LOADING,
                                enter = fadeIn(initialAlpha = 0.3f),
                                exit = fadeOut(targetAlpha = 0f),
                            ) {
                                NoteSkeleton()
                            }

                            AnimatedVisibility(
                                visible = saveStatus != SaveStatusNote.LOADING,
                                enter =
                                    fadeIn(
                                        initialAlpha = 0f,
                                        animationSpec =
                                            tween(
                                                durationMillis = 300,
                                                delayMillis = 100,
                                                easing = FastOutSlowInEasing,
                                            ),
                                    ),
                                exit =
                                    fadeOut(
                                        animationSpec =
                                            tween(
                                                durationMillis = 150,
                                                easing = LinearEasing,
                                            ),
                                    ),
                            ) {
                                Column {
                                    // TitleField
                                    TitleField(
                                        enabled = lockEditor,
                                        title = noteState.note.title,
                                        onTitleChange = { newTitle ->
                                            viewModel.updateTitle(newTitle)
                                        },
                                    )

                                    // DynamicBlocksEditor
                                    DynamicBlocksEditor(
                                        bottomSheetsOpen = { type, block, index ->
                                            AppLogger.d(
                                                "EditNoteScreen",
                                                "bottomSheetsOpen called with type: $type, index: $index"
                                            )
                                            bottomSheetsType = type
                                            scope.launch { sheetState.show() }
                                        },
                                        isLocked = lockEditor,
                                        enableScroll = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Edit menu
        EditNoteBottomSheet(
            isVisible = isEditMenuBottomSheetVisible,
            onDismiss = { isEditMenuBottomSheetVisible = false },
            onAction = {
                when (it) {
                    EditNoteBottomSheetAction.CLOSE_NO_SAVE -> {
                        scope.launch {
                            viewModel.saveNote(saveBackupVersion = true)
                            closeScreen()
                        }
                    }

                    EditNoteBottomSheetAction.SHARE -> {
                        shareText(
                            currentContext,
                            "${noteState.note.title}\n\n${
                                NotesTextFormatter.blocksToPlainText(
                                    viewModel.blocks
                                )
                            }"
                        )
                    }

                    EditNoteBottomSheetAction.DUPLICATE -> {
                        scope.launch {
                            viewModel.noteDuplicate()
                            closeScreen()
                        }
                    }

                    EditNoteBottomSheetAction.TRASH -> {
                        scope.launch {
                            viewModel.noteMoveToTrash()
                            closeScreen()
                        }
                    }
                }
            }
        )
    }
}


@Composable
private fun TopBarContent(
    isReadTrashOnly: Boolean,
    lockEditor: Boolean,
    onBackClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMenuClick: () -> Unit,
    onLockToggle: () -> Unit,
    onDoneClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isReadTrashOnly) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            AppBarTextButton(text = R.string.restore, onClick = onRestoreClick)
            Spacer(modifier = Modifier.width(10.dp))
            AppBarTextButton(text = R.string.delete_from_trash, onClick = onDeleteClick)
        } else {
            AppBarIconButton(icon = R.drawable.more, onPressed = onMenuClick)
            Spacer(modifier = Modifier.weight(1f))
            AppBarIconButton(
                icon = if (lockEditor) R.drawable.ic_lock else R.drawable.ic_unlock,
                tint = if (lockEditor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                onPressed = onLockToggle,
            )

            AnimatedVisibility(!lockEditor) {
                AppBarTextButton(text = R.string.done, onClick = onDoneClick)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
    }
}
