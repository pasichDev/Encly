package com.pasich.encly.presentation.screen

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyListRow
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.FieldState
import com.pasich.encly.presentation.designsystem.RowSkeleton
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.groupRowShape
import com.pasich.encly.presentation.dialogs.RequestDeleteTagsDialog
import com.pasich.encly.presentation.viewmodel.TAG_NAME_MAX_LENGTH
import com.pasich.encly.presentation.viewmodel.TagListEvent
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.presentation.viewmodel.TagOperationFailure
import com.pasich.encly.presentation.viewmodel.hasTagNamed
import com.pasich.encly.presentation.viewmodel.normalizeTagName
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val SKELETON_ROWS = 3

/** Keyboard for tag names: no suggestions or learning, as for every other vault text. */
private val TagNameKeyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)

/**
 * The tags screen: a field that creates a tag, then the tags as one grouped list (icon, name,
 * note count, drag handle). A tap opens the tag's sheet to rename, hide or delete it.
 */
@Composable
fun EditTagScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: TagListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val counts by viewModel.noteCounts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) { showTagFailures(viewModel, snackbarHostState, context) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        topBar = {
            EnclyTopBar(title = stringResource(R.string.main_drawer_tags), onBack = navController::popBackStack)
        },
    ) { padding ->
        TagsContent(
            state = TagsScreenState(state.tagsLoad, state.listTags, counts),
            actions = TagsActions(
                onCreate = { name, onResult -> viewModel.onEvent(TagListEvent.AddTag(Tag(nameTag = name), onResult)) },
                onOpen = { editingId = it.id },
                onMove = { from, to -> viewModel.onEvent(TagListEvent.ReorderTagsLive(from = from, to = to)) },
                onMoveDone = { viewModel.onEvent(TagListEvent.ReorderTags(it)) },
            ),
            modifier = Modifier
                .padding(padding)
                .imePadding(),
        )
    }

    val editing = state.listTags.firstOrNull { it.id == editingId }
    if (editing != null) {
        TagSheet(
            tag = editing,
            taken = { name -> state.listTags.hasTagNamed(name, exceptId = editing.id) },
            actions = TagSheetActions(
                onRename = { tag, onResult -> viewModel.onEvent(TagListEvent.UpdateTag(tag, onResult)) },
                onToggleVisible = { viewModel.onEvent(TagListEvent.ToggleVisibleTag(editing)) },
                onDelete = {
                    editingId = null
                    deleting = editing
                },
                onDismiss = { editingId = null },
            ),
        )
    }

    RequestDeleteTagsDialog(
        showDialog = deleting != null,
        onDismiss = { deleting = null },
        onConfirm = {
            deleting?.let { viewModel.onEvent(TagListEvent.DeleteTag(it)) }
            deleting = null
        },
    )
}

/** Failed tag operations as snackbars; a taken name is shown under the field that typed it. */
private suspend fun showTagFailures(
    viewModel: TagListViewModel,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    viewModel.operationFailures.collect { failure ->
        if (failure !=
            TagOperationFailure.NAME_TAKEN
        ) {
            snackbarHostState.showSnackbar(context.getString(failure.message()))
        }
    }
}

/** What the tag list renders: the load state, the tags in order and their note counts. */
private class TagsScreenState(val load: LoadState<List<Tag>>, val tags: List<Tag>, val counts: Map<Long, Int>)

/** What the tag list reports: a new name, a tapped tag, a drag step and a finished drag. */
private class TagsActions(
    val onCreate: (String, (Boolean) -> Unit) -> Unit,
    val onOpen: (Tag) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onMoveDone: (List<Tag>) -> Unit,
)

@Composable
private fun TagsContent(state: TagsScreenState, actions: TagsActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val currentTags by rememberUpdatedState(state.tags)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = currentTags.indexOfFirst { it.id == from.key }
        val toIndex = currentTags.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            actions.onMove(fromIndex, toIndex)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val spacing = EnclyTheme.spacing
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.gutter,
            end = spacing.gutter,
            top = spacing.s,
            bottom = spacing.l,
        ),
    ) {
        item(key = "new") {
            NewTagField(taken = { name -> state.tags.hasTagNamed(name) }, onCreate = actions.onCreate)
        }
        when (val load = state.load) {
            is LoadState.Loading -> items(SKELETON_ROWS) { RowSkeleton() }

            // A failed read is shown as an error, never as "no tags".
            is LoadState.Failed -> item(key = "failed") {
                EnclyEmptyState(
                    icon = EnclyIcons.Tag,
                    title = load.error.title.asString(),
                    body = load.error.message.asString(),
                    error = true,
                )
            }

            is LoadState.Ready -> if (state.tags.isEmpty()) {
                item(key = "empty") {
                    EnclyEmptyState(
                        icon = EnclyIcons.Tag,
                        title = stringResource(R.string.tags_empty_title),
                        body = stringResource(R.string.tags_empty_desc),
                    )
                }
            } else {
                tagRows(state, actions, reorderState, onDragStopped = { actions.onMoveDone(currentTags) })
            }
        }
    }
}

private fun LazyListScope.tagRows(
    state: TagsScreenState,
    actions: TagsActions,
    reorderState: ReorderableLazyListState,
    onDragStopped: () -> Unit,
) {
    val tags = state.tags
    item(key = "header") {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            modifier = Modifier.padding(top = EnclyTheme.spacing.l, bottom = EnclyTheme.spacing.s),
        ) {
            SectionOverline(stringResource(R.string.main_drawer_tags))
            Text(
                text = stringResource(R.string.tags_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(tags, key = { _, tag -> tag.id }) { index, tag ->
        ReorderableItem(reorderState, key = tag.id) { dragging ->
            Surface(
                shape = groupRowShape(index, tags.size),
                color = if (dragging) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shadowElevation = if (dragging) EnclyTheme.spacing.xs else 0.dp,
            ) {
                Column {
                    if (index > 0 && !dragging) EnclyGroupDivider()
                    TagRow(
                        tag = tag,
                        count = state.counts[tag.id] ?: 0,
                        onOpen = { actions.onOpen(tag) },
                        move = TagMove(
                            up = { moveAndSave(actions, tags, index, index - 1) }.takeIf { index > 0 },
                            down = { moveAndSave(actions, tags, index, index + 1) }.takeIf { index < tags.lastIndex },
                            onDragStopped = onDragStopped,
                        ),
                    )
                }
            }
        }
    }
}

/** One accessibility step: move the tag and save the new order at once. */
private fun moveAndSave(actions: TagsActions, tags: List<Tag>, from: Int, to: Int) {
    actions.onMove(from, to)
    actions.onMoveDone(tags.toMutableList().apply { add(to, removeAt(from)) })
}

/** How a row moves: the accessibility actions (null at the list's ends) and the drag's end. */
private class TagMove(val up: (() -> Unit)?, val down: (() -> Unit)?, val onDragStopped: () -> Unit)

/** A tag: its icon, name ("Hidden tag" under it when hidden), note count and the drag handle. */
@Composable
private fun ReorderableCollectionItemScope.TagRow(tag: Tag, count: Int, onOpen: () -> Unit, move: TagMove) {
    val moveUp = stringResource(R.string.tag_move_up)
    val moveDown = stringResource(R.string.tag_move_down)
    val handleDescription = stringResource(R.string.tag_drag_handle)
    EnclyListRow(
        title = tag.nameTag,
        supporting = stringResource(R.string.tag_hidden).takeUnless { tag.isVisible },
        icon = EnclyIcons.Tag,
        onClick = onOpen,
        modifier = Modifier
            .padding(start = EnclyTheme.spacing.s)
            .semantics {
                customActions = listOfNotNull(
                    move.up?.let { up ->
                        CustomAccessibilityAction(moveUp) {
                            up()
                            true
                        }
                    },
                    move.down?.let { down ->
                        CustomAccessibilityAction(moveDown) {
                            down()
                            true
                        }
                    },
                )
            },
    ) {
        Text(
            text = count.toString(),
            style = EnclyTheme.typography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(EnclyTheme.spacing.minTouchTarget)
                .draggableHandle(onDragStopped = { move.onDragStopped() })
                .semantics { contentDescription = handleDescription },
        ) {
            Icon(
                EnclyIcons.Grip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
    }
}

/** "Create a new tag": a labelled field, and "Add tag" once something is typed. */
@Composable
private fun NewTagField(taken: (String) -> Boolean, onCreate: (String, (Boolean) -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val name = normalizeTagName(text)
    val isTaken = name.isNotEmpty() && taken(name)
    val focusManager = LocalFocusManager.current

    fun create() {
        if (name.isEmpty() || isTaken) return
        onCreate(name) { added ->
            if (added) {
                text = ""
                focusManager.clearFocus()
            }
        }
    }

    // A typed but unsaved name is kept before the background re-lock drops this screen.
    SaveOnPause { create() }

    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        EnclyTextField(
            value = text,
            onValueChange = { text = it.take(TAG_NAME_MAX_LENGTH) },
            label = stringResource(R.string.create_new_tag),
            state = if (isTaken) FieldState.Error(stringResource(R.string.tag_name_taken)) else FieldState.Default,
            keyboardOptions = TagNameKeyboard,
            keyboardActions = KeyboardActions(onDone = { create() }),
        )
        AnimatedVisibility(visible = name.isNotEmpty()) {
            EnclyButton(
                text = stringResource(R.string.tag_add),
                onClick = ::create,
                enabled = !isTaken,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the tag sheet does: save a new name, flip the chip, ask to delete, close. */
private class TagSheetActions(
    val onRename: (Tag, (Boolean) -> Unit) -> Unit,
    val onToggleVisible: () -> Unit,
    val onDelete: () -> Unit,
    val onDismiss: () -> Unit,
)

/** A tag's sheet: rename it (Save), show or hide its chip, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(tag: Tag, taken: (String) -> Boolean, actions: TagSheetActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(tag.id) { mutableStateOf(tag.nameTag) }
    val name = normalizeTagName(text)
    val isTaken = name.isNotEmpty() && taken(name)
    val canSave = name.isNotEmpty() && !isTaken && name != tag.nameTag

    fun save() {
        if (!canSave) return
        actions.onRename(tag.copy(nameTag = name)) { saved ->
            if (saved) scope.launch { sheetState.hide() }.invokeOnCompletion { actions.onDismiss() }
        }
    }

    SaveOnPause { save() }

    EnclyBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        title = stringResource(R.string.tag_edit),
    ) {
        EnclyTextField(
            value = text,
            onValueChange = { text = it.take(TAG_NAME_MAX_LENGTH) },
            label = stringResource(R.string.tag_name_label),
            state = if (isTaken) FieldState.Error(stringResource(R.string.tag_name_taken)) else FieldState.Default,
            keyboardOptions = TagNameKeyboard,
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        EnclySwitchRow(
            title = stringResource(R.string.tag_show_in_list),
            supporting = stringResource(R.string.tag_show_in_list_desc),
            checked = tag.isVisible,
            onCheckedChange = { actions.onToggleVisible() },
        )
        EnclySheetRow(
            title = stringResource(R.string.delete_tag),
            icon = EnclyIcons.Trash,
            destructive = true,
            onClick = actions.onDelete,
        )
        EnclyButton(
            text = stringResource(R.string.save),
            onClick = ::save,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = EnclyTheme.spacing.s),
        )
    }
}

/** What the user is told when a tag operation fails. */
@StringRes
private fun TagOperationFailure.message(): Int = when (this) {
    TagOperationFailure.CREATE -> R.string.tag_create_failed
    TagOperationFailure.UPDATE -> R.string.tag_update_failed
    TagOperationFailure.DELETE -> R.string.tag_delete_failed
    TagOperationFailure.REORDER -> R.string.tag_reorder_failed
    TagOperationFailure.NAME_TAKEN -> R.string.tag_name_taken
}

/**
 * Runs [onPause] when the screen's lifecycle pauses. Leaving the app re-locks the vault and
 * pops every screen, so inline edits that are not saved here are lost.
 */
@Composable
private fun SaveOnPause(onPause: () -> Unit) {
    val currentOnPause by rememberUpdatedState(onPause)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentOnPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
