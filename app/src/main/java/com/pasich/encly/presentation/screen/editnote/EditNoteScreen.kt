package com.pasich.encly.presentation.screen.editnote

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LockOpen
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.components.editNote.NoteBottomBar
import com.pasich.encly.presentation.components.editNote.NoteSubTitle
import com.pasich.encly.presentation.components.editNote.TitleField
import com.pasich.encly.presentation.designsystem.EnclyBackButton
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheet
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheetAction
import com.pasich.encly.presentation.dialogs.blocks.ActionBlockDialog
import com.pasich.encly.presentation.dialogs.blocks.ActionLinkBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.ActionOtherBottomSheet
import com.pasich.encly.presentation.dialogs.blocks.SettingsBlockDialog
import com.pasich.encly.presentation.editor.DynamicBlocksEditor
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun EditNoteScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
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
    // Re-read when the block the user works on changes; blocks are read below, so their changes count too.
    val interactedBlockId by viewModel.interactedBlockId.collectAsState()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.saveForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
    var sheetBlockId by rememberSaveable { mutableStateOf<String?>(null) }

    fun showWriteFailure(@StringRes message: Int = R.string.note_save_failed) {
        Toast.makeText(currentContext, currentContext.getString(message), Toast.LENGTH_LONG).show()
    }

    fun finishNavigation() {
        viewModel.markExitHandled()
        keyboardController?.hide()
        focusManager.clearFocus()
        navController.popBackStack()
    }

    fun closeScreen() {
        scope.launch {
            if (viewModel.saveNote()) {
                finishNavigation()
            } else {
                showWriteFailure()
            }
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
                val deleted = viewModel.noteDelete()
                isDialogVisible = false
                if (deleted) {
                    finishNavigation()
                } else {
                    showWriteFailure(R.string.note_delete_failed)
                }
            }
        },
        onDismiss = {
            isDialogVisible = false
        },
        destructive = true,
    )

    // The sheet targets one block by identity. Resolved on every composition, so a sheet can
    // never read a position that undo/redo/delete already removed.
    val sheetBlock = sheetBlockId?.let { id -> viewModel.blocks.firstOrNull { it.id == id } }
    val blockMove = remember(interactedBlockId, viewModel.blocks.toList()) { viewModel.getMoveBlockState() }

    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            bottomSheetsType = BottomSheetsOpenType.NONE
            sheetBlockId = null
        }
    }

    fun onBlockSheetAction(action: ActionBlockDialog) {
        val link = (sheetBlock as? Block.LinkBlock)?.block?.value?.url.orEmpty()
        when (action) {
            ActionBlockDialog.Delete -> sheetBlock?.let { viewModel.removeBlock(it, BlockRemoveAction.REMOVE) }
            is ActionBlockDialog.Move -> viewModel.moveBlock(action.move == 1)
            ActionBlockDialog.OpenLink -> openNoteLink(currentContext, link)
            ActionBlockDialog.CopyLink -> copyNoteLink(currentContext, link)
            ActionBlockDialog.EditLink -> (sheetBlock as? Block.LinkBlock)?.let(viewModel::editLink)
        }
        if (action !is ActionBlockDialog.Move) dismissSheet()
    }

    val canEditBlocks = !lockEditor && !viewModel.isReadTrashOnly

    if (sheetBlock != null && bottomSheetsType == BottomSheetsOpenType.ACTION_OTHER) {
        ActionOtherBottomSheet(
            settings = SettingsBlockDialog(
                block = sheetBlock,
                isBottomSheetVisible = true,
                blockMove = blockMove,
                canEdit = canEditBlocks,
            ),
            sheetState = sheetState,
            onDismiss = ::dismissSheet,
            onAction = ::onBlockSheetAction,
        )
    }

    if (sheetBlock != null && bottomSheetsType == BottomSheetsOpenType.ACTION_LINK) {
        ActionLinkBottomSheet(
            sheetState = sheetState,
            settings = SettingsBlockDialog(
                block = sheetBlock,
                isBottomSheetVisible = true,
                blockMove = blockMove,
                canEdit = canEditBlocks,
            ),
            onDismiss = ::dismissSheet,
            onAction = ::onBlockSheetAction,
        )
    }

    EditNoteSettingsProvider(
        baseFontSize = fontSize.sp,
        fontStyle = fontStyle,
        simpleEdit = simpleEdit,
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.ime,
            bottomBar = {
                if (imeVisible && !lockEditor && !viewModel.isReadTrashOnly) {
                    // NoteBottomBar resolves the same back-stack-scoped EditNoteViewModel itself.
                    NoteBottomBar(
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
                            actions = EditorBarActions(
                                onBack = { closeScreen() },
                                onRestore = {
                                    scope.launch {
                                        if (viewModel.noteRestore()) {
                                            finishNavigation()
                                        } else {
                                            showWriteFailure(R.string.note_restore_failed)
                                        }
                                    }
                                },
                                onDelete = { isDialogVisible = true },
                                onMenu = { isEditMenuBottomSheetVisible = true },
                                onLockToggle = { viewModel.toggleLockEditor() },
                            ),
                        )
                    }

                    // NoteSubTitle
                    if (!lockEditor && !viewModel.isReadTrashOnly) {
                        item {
                            NoteSubTitle(
                                tagButtonPadding = PaddingValues(
                                    horizontal = EnclyTheme.spacing.gutter,
                                    vertical = EnclyTheme.spacing.xs,
                                ),
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
                                NoteSkeleton(modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter))
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
                                        readOnly = lockEditor,
                                        title = noteState.note.title,
                                        onTitleChange = { newTitle ->
                                            viewModel.updateTitle(newTitle)
                                        },
                                    )

                                    // DynamicBlocksEditor
                                    DynamicBlocksEditor(
                                        bottomSheetsOpen = { type, block, index ->
                                            bottomSheetsType = type
                                            sheetBlockId = block.id
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
                            if (viewModel.discardChanges()) {
                                finishNavigation()
                            } else {
                                showWriteFailure(R.string.note_discard_failed)
                            }
                        }
                    }

                    EditNoteBottomSheetAction.DUPLICATE -> {
                        scope.launch {
                            val saved = viewModel.saveNote()
                            val duplicateId = if (saved) viewModel.noteDuplicate() else -1L
                            if (duplicateId > 0L) {
                                finishNavigation()
                            } else {
                                showWriteFailure(R.string.note_duplicate_failed)
                            }
                        }
                    }

                    EditNoteBottomSheetAction.TRASH -> {
                        scope.launch {
                            if (viewModel.noteMoveToTrash()) {
                                finishNavigation()
                            } else {
                                showWriteFailure(R.string.note_trash_failed)
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * The editor bar: back (saves and closes), then Lock editing and More actions. In the trash:
 * back, Restore and Delete.
 */
private class EditorBarActions(
    val onBack: () -> Unit,
    val onRestore: () -> Unit,
    val onDelete: () -> Unit,
    val onMenu: () -> Unit,
    val onLockToggle: () -> Unit,
)

@Composable
private fun TopBarContent(isReadTrashOnly: Boolean, lockEditor: Boolean, actions: EditorBarActions) {
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
        if (isReadTrashOnly) {
            EnclyTextButton(text = stringResource(R.string.restore), onClick = actions.onRestore)
            EnclyTextButton(
                text = stringResource(R.string.delete_from_trash),
                onClick = actions.onDelete,
                destructive = true,
            )
        } else {
            IconButton(onClick = actions.onLockToggle) {
                Icon(
                    if (lockEditor) Lucide.Lock else Lucide.LockOpen,
                    contentDescription = stringResource(
                        if (lockEditor) R.string.editor_unlock else R.string.editor_lock,
                    ),
                    tint = if (lockEditor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = actions.onMenu) {
                Icon(
                    Lucide.EllipsisVertical,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
