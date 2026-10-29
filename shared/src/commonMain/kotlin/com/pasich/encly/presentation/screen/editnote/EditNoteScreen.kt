package com.pasich.encly.presentation.screen.editnote

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.savedstate.read
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.dialog_message
import com.pasich.encly.core.resources.dialog_title
import com.pasich.encly.core.resources.markdown_copied
import com.pasich.encly.core.resources.markdown_copy
import com.pasich.encly.core.resources.note_discard_confirm_message
import com.pasich.encly.core.resources.note_discard_confirm_title
import com.pasich.encly.core.resources.swipe_delete_message
import com.pasich.encly.core.resources.swipe_delete_title
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.theme.blockGap
import com.pasich.encly.editor.theme.isCompact
import com.pasich.encly.editor.ui.EditorBlocksHost
import com.pasich.encly.editor.ui.FindHighlights
import com.pasich.encly.editor.ui.editorBlocks
import com.pasich.encly.editor.ui.rememberBlockFocusRegistry
import com.pasich.encly.editor.ui.rememberWriteAtEnd
import com.pasich.encly.editor.ui.tapBelowBlocks
import com.pasich.encly.platform.LocalSensitiveClipboard
import com.pasich.encly.presentation.components.editNote.NoteBottomBar
import com.pasich.encly.presentation.components.editNote.NoteOverline
import com.pasich.encly.presentation.components.editNote.TitleField
import com.pasich.encly.presentation.designsystem.EnclyScrollbar
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheet
import com.pasich.encly.presentation.dialogs.EditNoteBottomSheetAction
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.effects.ContainerEnd
import com.pasich.encly.presentation.effects.rememberNoteContainerModifier
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.pane.EditorPane
import com.pasich.encly.presentation.screen.backup.MarkdownExportHost
import com.pasich.encly.presentation.viewmodel.DocumentFormat
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.MarkdownExportTarget
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.noteColorStyle
import com.pasich.encly.utils.currentLanguageTag
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Items above the blocks: the overline and the title. */
private const val HEADER_ITEMS = 2

@Composable
@Suppress("LongMethod") // Screen state and editor settings feed one scoped editor composition.
fun EditNoteScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    pane: EditorPane? = null,
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // A new language rebuilds the exits (their texts), as a new configuration did.
    val language = currentLanguageTag()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val entry = lifecycleOwner as? NavBackStackEntry
    // The route's argument, not the ViewModel's id: a new note gets its id on the first save, and
    // only an existing, editable note has a card to grow from.
    // An editor in the detail pane sits beside its card, so there is nothing to grow from either.
    val plain = pane != null || viewModel.isReadTrashOnly ||
        entry?.savedStateHandle?.contains(RelockReturn.PLAIN_ENTRY) == true
    val cardNoteId = if (plain) -1L else entry?.arguments?.read { getLongOrNull("idNote") } ?: -1L
    val container = rememberNoteContainerModifier(cardNoteId, ContainerEnd.EDITOR)
    val exits = remember(viewModel, navController, language, pane) {
        EditorExits(
            viewModel = viewModel,
            where = if (pane != null) PaneEditorPlace(pane) else ScreenEditorPlace(navController, entry),
            scope = scope,
            onFailure = { message -> scope.launch { snackbarHostState.showSnackbar(getString(message)) } },
            beforeLeaving = {
                keyboardController?.hide()
                focusManager.clearFocus()
            },
        )
    }
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val fontStyle by viewModel.fontStyle.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.noteLineSpacing.collectAsStateWithLifecycle()
    val simpleEdit by viewModel.simpleEdit.collectAsStateWithLifecycle()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    val editedElsewhere by viewModel.editedElsewhere.collectAsState()
    val overlays = rememberEditorOverlays()
    val listState = rememberLazyListState()
    val focusRegistry = rememberBlockFocusRegistry(viewModel.blocks)
    val findState = viewModel.find.collectAsState()
    val highlights = remember(findState) { FindHighlights(findState) }
    val controller = rememberEditorController()
    val host = remember(controller, focusRegistry, highlights) {
        EditorBlocksHost(controller, focusRegistry, highlights) { type, block ->
            overlays.blockSheet.open(type, block.id)
        }
    }
    val titleFocus = remember { FocusRequester() }

    SaveWhenPaused()
    if (pane != null) PaneTabBinding(pane, exits)
    val documents = rememberEditorDocumentExport(exits)
    EditorShortcutCommands(overlays, documents)
    ClaimNoteForEditing()
    val blocksStart = HEADER_ITEMS + (if (contentLoadFailed) 1 else 0) + (if (editedElsewhere) 1 else 0)
    CarryOutFocusRequests(focusRegistry, listState, blocksStart = blocksStart)
    ScrollToFindMatch(listState, blocksStart)
    FocusNewNoteTitle(titleFocus)
    BackHandler { exits.back() }
    CloseFindOrSlashOnBack()
    EditNoteSettingsProvider(
        baseFontSize = fontSize.sp,
        fontStyle = fontStyle,
        simpleEdit = simpleEdit,
        lineSpacing = lineSpacing,
    ) {
        // Inside the provider: the block sheets are the editor's, and read its strings and theme.
        EditorOverlays(
            overlays = overlays,
            exits = exits,
            documents = documents,
            onManageTags = { navController.navigate(NavRoutes.EditTagRoute.name) },
            onMessage = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
        )
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
            modifier = modifier.then(container),
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
    viewModel: EditNoteViewModel = metroViewModel(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val lockEditor by viewModel.lockEditor.collectAsState()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    val findOpen by rememberFindOpen()
    val pageColor = rememberPageColor()
    Scaffold(
        modifier = modifier.onPreviewKeyEvent { event ->
            viewModel.handleHistoryKey(event)
        },
        containerColor = pageColor,
        // Status and navigation bars, a display cutout and the keyboard all stay clear of text.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                EditorTopBar(
                    state = EditorBarState(
                        isReadTrashOnly = viewModel.isReadTrashOnly,
                        lockEditor = lockEditor,
                        canToggleLock = !contentLoadFailed,
                        containerColor = pageColor,
                    ),
                    listState = listState,
                    actions = actions,
                )
                if (findOpen) EditorFindBar()
            }
        },
        bottomBar = {
            if (!lockEditor && !viewModel.isReadTrashOnly) NoteBottomBar(simpleEdit = LocalSimpleEdit.current)
        },
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        content = content,
    )
}

/**
 * The editor's page colour: the theme's surface, tinted by the note's colour. Read through derived
 * state, so a title keystroke does not recompose the editor; a colour change fades in.
 */
@Composable
private fun rememberPageColor(viewModel: EditNoteViewModel = metroViewModel()): Color {
    val noteState = viewModel.state.collectAsState()
    val colorKey by remember(noteState) { derivedStateOf { noteState.value.note.color } }
    val scheme = MaterialTheme.colorScheme
    val target = remember(colorKey, scheme) {
        noteColorStyle(NoteColor.fromKey(colorKey), scheme)?.page ?: scheme.surface
    }
    return animateColorAsState(target, label = "pageColor").value
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
    var isTrashDialogVisible by mutableStateOf(false)
}

@Composable
private fun rememberEditorOverlays(): EditorOverlaysState {
    val blockSheet = rememberBlockSheetState()
    val editMenu = rememberSaveable { mutableStateOf(false) }
    val tagSheet = rememberSaveable { mutableStateOf(false) }
    return remember { EditorOverlaysState(blockSheet, editMenu, tagSheet) }
}

/** The editor's sheets and dialogs: block sheets, tags, More actions, delete and discard. */
@Suppress("LongMethod") // One sheet, its dialogs and the actions of its rows, wired in one place.
@Composable
private fun EditorOverlays(
    overlays: EditorOverlaysState,
    exits: EditorExits,
    documents: EditorDocumentExport,
    onManageTags: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: EditNoteViewModel = metroViewModel(),
    markdownViewModel: MarkdownExportViewModel = metroViewModel(),
) {
    val clipboard = LocalSensitiveClipboard.current
    val copyLabel = stringResource(Res.string.markdown_copy)
    val copyConfirmation = stringResource(Res.string.markdown_copied)
    val lockEditor by viewModel.lockEditor.collectAsState()
    EditorConfirmDialogs(
        visible = ConfirmationsVisible(
            delete = overlays.isDeleteDialogVisible,
            discard = overlays.isDiscardDialogVisible,
            trash = overlays.isTrashDialogVisible,
        ),
        onDelete = exits::delete,
        onDiscard = exits::discard,
        onTrash = exits::trash,
        onDismiss = {
            overlays.isDeleteDialogVisible = false
            overlays.isDiscardDialogVisible = false
            overlays.isTrashDialogVisible = false
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
    MarkdownExportHost(onMessage)
    EditNoteBottomSheet(
        isVisible = overlays.isEditMenuVisible,
        onDismiss = { overlays.isEditMenuVisible = false },
        documents = documents.available,
        onAction = { action ->
            when (action) {
                EditNoteBottomSheetAction.CLOSE_NO_SAVE -> exits.discardOrConfirm {
                    overlays.isDiscardDialogVisible =
                        true
                }

                EditNoteBottomSheetAction.DUPLICATE -> exits.duplicate()

                EditNoteBottomSheetAction.FIND -> viewModel.openFind()

                EditNoteBottomSheetAction.TRASH -> exits.trash()

                EditNoteBottomSheetAction.EXPORT_MARKDOWN -> exits.exportMarkdown { id ->
                    markdownViewModel.start(MarkdownExportTarget.SingleNote(id))
                }

                EditNoteBottomSheetAction.EXPORT_PDF -> documents.start(DocumentFormat.PDF)

                EditNoteBottomSheetAction.PRINT -> documents.start(DocumentFormat.PRINT)

                EditNoteBottomSheetAction.COPY_MARKDOWN -> copyNoteAsMarkdown(
                    clipboard,
                    viewModel,
                    copyLabel,
                    copyConfirmation,
                )?.let(onMessage)

                EditNoteBottomSheetAction.ARCHIVE -> exits.archive(archived = true)

                EditNoteBottomSheetAction.UNARCHIVE -> exits.archive(archived = false)
            }
        },
    )
}

/** The editor's list and, on a desktop, its scrollbar. */
@Composable
private fun EditorContent(
    host: EditorBlocksHost,
    listState: LazyListState,
    titleFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        EditorBlocks(host, listState, titleFocus)
        EnclyScrollbar(listState)
    }
}

/** The editor's list: overline, title, a notice when the content is unreadable, then the blocks. */
@Composable
private fun EditorBlocks(
    host: EditorBlocksHost,
    listState: LazyListState,
    titleFocus: FocusRequester,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val spacing = LocalEditorTheme.current.lineSpacing
    val controller = rememberEditorController()
    val headerGap = if (spacing.isCompact) EnclyTheme.spacing.labelGap else 0.dp
    val lockEditor by viewModel.lockEditor.collectAsState()
    val contentLoadFailed by viewModel.contentLoadFailed.collectAsState()
    val editedElsewhere by viewModel.editedElsewhere.collectAsState()
    val editingLinkIds by viewModel.editingLinkIds.collectAsState()
    val templatesOffered by viewModel.templatesOffered.collectAsState()
    // Only whether it is loading: a save changing the status must not recompose the list.
    val status = viewModel.status.collectAsState()
    val loading by remember(status) { derivedStateOf { status.value == SaveStatusNote.LOADING } }

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyColumn(
            state = listState,
            // Default block frames and this gap give 14 dp between blocks (spec §4.4).
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap(EnclyTheme.spacing.labelGap)),
            contentPadding = PaddingValues(top = EnclyTheme.spacing.xxs),
            modifier = modifier
                .fillMaxSize()
                // The empty page under a short note is part of the note: a tap there writes at its end, keyboard up.
                .tapBelowBlocks(listState, enabled = !lockEditor, onTap = rememberWriteAtEnd(controller)),
        ) {
            if (loading) {
                item(key = "skeleton") {
                    NoteSkeleton(modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter))
                }
                return@LazyColumn
            }
            item(key = "overline") {
                EditorOverline(modifier = Modifier.padding(bottom = headerGap))
            }
            item(key = "title") {
                EditorTitle(
                    readOnly = lockEditor,
                    modifier = Modifier
                        .focusRequester(titleFocus)
                        .padding(bottom = headerGap),
                )
            }
            editorNotices(contentLoadFailed, editedElsewhere, headerGap)
            editorBlocks(
                host,
                viewModel.blocks,
                isLocked = lockEditor,
                editingLinkIds = editingLinkIds,
                afterBlocks = if (templatesOffered) ({ TemplateChooser() }) else null,
            )
        }
    }
}

/** The title field, bound to the note's title; "Next" moves on to the first block. */
@Composable
private fun EditorTitle(
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = metroViewModel(),
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
    viewModel: EditNoteViewModel = metroViewModel(),
    tagViewModel: TagListViewModel = metroViewModel(),
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

/** Which of the editor's confirmations are open. */
private class ConfirmationsVisible(val delete: Boolean, val discard: Boolean, val trash: Boolean)

/** Delete (from the trash), discard, and moving to the trash from the keyboard all ask first. */
@Composable
private fun EditorConfirmDialogs(
    visible: ConfirmationsVisible,
    onDelete: () -> Unit,
    onDiscard: () -> Unit,
    onTrash: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        isVisible = visible.delete,
        titleText = stringResource(Res.string.dialog_title),
        messageText = stringResource(Res.string.dialog_message),
        onConfirm = {
            onDismiss()
            onDelete()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
    ConfirmDialog(
        isVisible = visible.discard,
        titleText = stringResource(Res.string.note_discard_confirm_title),
        messageText = stringResource(Res.string.note_discard_confirm_message),
        onConfirm = {
            onDismiss()
            onDiscard()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
    ConfirmDialog(
        isVisible = visible.trash,
        titleText = stringResource(Res.string.swipe_delete_title),
        messageText = stringResource(Res.string.swipe_delete_message),
        onConfirm = {
            onDismiss()
            onTrash()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
}
