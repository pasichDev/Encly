package com.pasich.encly.presentation.screen.editnote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.components.editNote.NoteBottomBar
import com.pasich.encly.presentation.components.editNote.NoteOverline
import com.pasich.encly.presentation.components.editNote.TitleField
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheet
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheetAction
import com.pasich.encly.presentation.editor.EditorBlocksHost
import com.pasich.encly.presentation.editor.editorBlocks
import com.pasich.encly.presentation.editor.focus.rememberBlockFocusRegistry
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

/** Items above the blocks: the overline and the title. */
private const val HEADER_ITEMS = 2

@Composable
fun EditNoteScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exits = remember(viewModel, navController) {
        EditorExits(
            viewModel = viewModel,
            navController = navController,
            entry = lifecycleOwner as? NavBackStackEntry,
            scope = scope,
            onFailure = { message -> scope.launch { snackbarHostState.showSnackbar(context.getString(message)) } },
            beforeLeaving = {
                keyboardController?.hide()
                focusManager.clearFocus()
            },
        )
    }
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val fontStyle by viewModel.fontStyle.collectAsStateWithLifecycle()
    val simpleEdit by viewModel.simpleEdit.collectAsStateWithLifecycle()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    val overlays = rememberEditorOverlays()
    val listState = rememberLazyListState()
    val focusRegistry = rememberBlockFocusRegistry(viewModel.blocks)
    val host = remember(viewModel, focusRegistry) {
        EditorBlocksHost(viewModel, focusRegistry) { type, block -> overlays.blockSheet.open(type, block.id) }
    }
    val titleFocus = remember { FocusRequester() }

    SaveWhenPaused()
    CarryOutFocusRequests(focusRegistry, listState, blocksStart = HEADER_ITEMS + if (contentLoadFailed) 1 else 0)
    FocusNewNoteTitle(titleFocus)
    BackHandler { exits.back() }
    EditorOverlays(overlays, exits, onManageTags = { navController.navigate(NavRoutes.EditTagRoute.name) })

    EditNoteSettingsProvider(baseFontSize = fontSize.sp, fontStyle = fontStyle, simpleEdit = simpleEdit) {
        EditorScaffold(
            actions = EditorBarActions(
                onBack = exits::back,
                onRestore = exits::restore,
                onDelete = { overlays.isDeleteDialogVisible = true },
                onTags = { overlays.isTagSheetVisible = true },
                onMenu = { overlays.isEditMenuVisible = true },
                onLockToggle = viewModel::toggleLockEditor,
            ),
            listState = listState,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        ) { padding ->
            EditorContent(
                host = host,
                listState = listState,
                titleFocus = titleFocus,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/**
 * The editor's frame: the pinned bar, and the formatting toolbar, always there while the note
 * can be edited, keyboard or not (a hardware keyboard never shows one); it lifts itself above
 * the keyboard.
 */
@Composable
private fun EditorScaffold(
    actions: EditorBarActions,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val lockEditor by viewModel.lockEditor.collectAsState()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        // Status and navigation bars, a display cutout and the keyboard all stay clear of text.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            EditorTopBar(
                state = EditorBarState(
                    isReadTrashOnly = viewModel.isReadTrashOnly,
                    lockEditor = lockEditor,
                    canToggleLock = !contentLoadFailed,
                ),
                listState = listState,
                actions = actions,
            )
        },
        bottomBar = {
            if (!lockEditor && !viewModel.isReadTrashOnly) NoteBottomBar(simpleEdit = LocalSimpleEdit.current)
        },
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        content = content,
    )
}

/** Which of the editor's sheets and dialogs are open. */
internal class EditorOverlaysState(
    val blockSheet: BlockSheetState,
    editMenu: MutableState<Boolean>,
    tagSheet: MutableState<Boolean>,
) {
    var isEditMenuVisible by editMenu
    var isTagSheetVisible by tagSheet
    var isDeleteDialogVisible by mutableStateOf(false)
    var isDiscardDialogVisible by mutableStateOf(false)
}

@Composable
private fun rememberEditorOverlays(): EditorOverlaysState {
    val blockSheet = rememberBlockSheetState()
    val editMenu = rememberSaveable { mutableStateOf(false) }
    val tagSheet = rememberSaveable { mutableStateOf(false) }
    return remember { EditorOverlaysState(blockSheet, editMenu, tagSheet) }
}

/** The editor's sheets and dialogs: block sheets, tags, More actions, delete and discard. */
@Composable
private fun EditorOverlays(
    overlays: EditorOverlaysState,
    exits: EditorExits,
    onManageTags: () -> Unit,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val lockEditor by viewModel.lockEditor.collectAsState()
    EditorConfirmDialogs(
        isDeleteVisible = overlays.isDeleteDialogVisible,
        isDiscardVisible = overlays.isDiscardDialogVisible,
        onDelete = exits::delete,
        onDiscard = exits::discard,
        onDismiss = {
            overlays.isDeleteDialogVisible = false
            overlays.isDiscardDialogVisible = false
        },
    )
    EditorBlockSheets(overlays.blockSheet, canEdit = !lockEditor && !viewModel.isReadTrashOnly)
    if (overlays.isTagSheetVisible) {
        EditorTagSheet(
            onManageTags = {
                overlays.isTagSheetVisible = false
                onManageTags()
            },
            onDismiss = { overlays.isTagSheetVisible = false },
        )
    }
    EditNoteBottomSheet(
        isVisible = overlays.isEditMenuVisible,
        onDismiss = { overlays.isEditMenuVisible = false },
        onAction = { action ->
            when (action) {
                EditNoteBottomSheetAction.CLOSE_NO_SAVE -> exits.discardOrConfirm {
                    overlays.isDiscardDialogVisible =
                        true
                }

                EditNoteBottomSheetAction.DUPLICATE -> exits.duplicate()

                EditNoteBottomSheetAction.TRASH -> exits.trash()
            }
        },
    )
}

/** The editor's list: overline, title, a notice when the content is unreadable, then the blocks. */
@Composable
private fun EditorContent(
    host: EditorBlocksHost,
    listState: LazyListState,
    titleFocus: FocusRequester,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val lockEditor by viewModel.lockEditor.collectAsState()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    val editingLinkIds by viewModel.editingLinkIds.collectAsState()
    // Only whether it is loading: a save changing the status must not recompose the list.
    val status = viewModel.status.collectAsState()
    val loading by remember(status) { derivedStateOf { status.value == SaveStatusNote.LOADING } }

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyColumn(
            state = listState,
            // With a block's 4 dp frame inset above and below, blocks sit 14 apart (spec §4.4).
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
            contentPadding = PaddingValues(top = EnclyTheme.spacing.xxs),
            modifier = modifier.fillMaxSize(),
        ) {
            if (loading) {
                item(key = "skeleton") {
                    NoteSkeleton(modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter))
                }
                return@LazyColumn
            }
            item(key = "overline") { EditorOverline() }
            item(key = "title") { EditorTitle(readOnly = lockEditor, modifier = Modifier.focusRequester(titleFocus)) }
            if (contentLoadFailed) {
                item(key = "unreadable") {
                    EnclyCallout(
                        text = stringResource(R.string.note_load_failed),
                        tone = CalloutTone.WARNING,
                        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter),
                    )
                }
            }
            editorBlocks(host, viewModel.blocks, isLocked = lockEditor, editingLinkIds = editingLinkIds)
        }
    }
}

/** The title field, bound to the note's title; "Next" moves on to the first block. */
@Composable
private fun EditorTitle(
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    TitleField(
        title = { viewModel.state.value.note.title },
        titleChanges = viewModel.state,
        onTitleChange = viewModel::updateTitle,
        onNext = viewModel::focusFirstBlock,
        readOnly = readOnly,
        modifier = modifier,
    )
}

/** The overline: the note's tag and when it was edited, and "Not saved" after a failed save. */
@Composable
private fun EditorOverline(
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = hiltViewModel(),
    tagViewModel: TagListViewModel = hiltViewModel(),
) {
    // Read through derived state: a title keystroke or a save does not recompose the overline.
    val noteState = viewModel.state.collectAsState()
    val status = viewModel.status.collectAsState()
    val meta by remember(noteState) { derivedStateOf { noteState.value.note.let { it.tagId to it.date } } }
    val notSaved by remember(status) { derivedStateOf { status.value == SaveStatusNote.FAILED } }
    val tagState by tagViewModel.state.collectAsState()
    val (tagId, date) = meta
    NoteOverline(
        tagName = tagState.listTags.firstOrNull { it.id == tagId }?.nameTag,
        date = date,
        notSaved = notSaved,
        modifier = modifier.padding(
            start = EnclyTheme.spacing.gutter,
            end = EnclyTheme.spacing.gutter,
            bottom = EnclyTheme.spacing.xs,
        ),
    )
}

/** Delete (from the trash) and discard both ask first. */
@Composable
private fun EditorConfirmDialogs(
    isDeleteVisible: Boolean,
    isDiscardVisible: Boolean,
    onDelete: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        isVisible = isDeleteVisible,
        titleText = stringResource(R.string.dialog_title),
        messageText = stringResource(R.string.dialog_message),
        onConfirm = {
            onDismiss()
            onDelete()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
    ConfirmDialog(
        isVisible = isDiscardVisible,
        titleText = stringResource(R.string.note_discard_confirm_title),
        messageText = stringResource(R.string.note_discard_confirm_message),
        onConfirm = {
            onDismiss()
            onDiscard()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
}
