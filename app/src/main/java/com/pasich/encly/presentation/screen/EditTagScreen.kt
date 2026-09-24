package com.pasich.encly.presentation.screen

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyListRow
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.groupRowShape
import com.pasich.encly.presentation.dialogs.RequestDeleteTagsDialog
import com.pasich.encly.presentation.viewmodel.TagListEvent
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.presentation.viewmodel.TagOperationFailure
import com.pasich.encly.ui.theme.EnclyTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.composables.icons.lucide.Tag as TagIcon

/** Longest tag name the editor accepts. */
private const val TAG_NAME_MAX_LENGTH = 20

enum class TagEditTypeAction {
    EDIT,
    VISIBLE,
    DELETE,
}

// Focus management state
@Composable
private fun rememberFocusManager() = LocalFocusManager.current

@Composable
fun EditTagScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val focusManager = rememberFocusManager()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // Clear focus when clicking outside
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(
                title = stringResource(R.string.edit_tags),
                onBack = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    navController.popBackStack()
                },
            )
        },
    ) { padding ->
        ListTagsEdit(
            modifier = Modifier.padding(padding),
            globalFocusManager = focusManager,
            keyboardController = keyboardController,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
private fun ListTagsEdit(
    globalFocusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    modifier: Modifier = Modifier,
    viewModel: TagListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var tagDelete by remember { mutableStateOf(Tag()) }
    val listState = rememberLazyListState()

    // Track currently focused item to prevent multiple focuses
    var currentlyFocusedItemId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.operationFailures.collect { failure ->
            Toast.makeText(context, context.getString(failure.message()), Toast.LENGTH_LONG).show()
        }
    }

    // Clear focus when dialog is shown
    LaunchedEffect(showDialog) {
        if (showDialog) {
            globalFocusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val failure = (state.tagsLoad as? LoadState.Failed)?.error
    AnimatedVisibility(visible = failure != null, enter = fadeIn(), exit = fadeOut()) {
        // A failed read is shown as an error, never as "no tags" with an editor over it.
        EnclyEmptyState(
            icon = Lucide.TagIcon,
            modifier = modifier,
            title = failure?.title?.asString(),
            body = failure?.message?.asString(),
            error = true,
        )
    }

    AnimatedVisibility(
        visible = state.tagsLoad is LoadState.Loading,
        enter = fadeIn(initialAlpha = 0.3f),
        exit = fadeOut(targetAlpha = 0f),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }

    AnimatedVisibility(
        visible = state.tagsLoad is LoadState.Ready,
        enter = fadeIn(
            initialAlpha = 0f,
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = 100,
                easing = FastOutSlowInEasing,
            ),
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 150,
                easing = LinearEasing,
            ),
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // Clear focus when clicking empty space
                    globalFocusManager.clearFocus()
                    keyboardController?.hide()
                    currentlyFocusedItemId = null
                },
        ) {
            NewTagItem(
                item = "",
                onSave = { tag, onResult ->
                    viewModel.onEvent(
                        TagListEvent.AddTag(tag) { success ->
                            if (success) {
                                currentlyFocusedItemId = null
                            }
                            onResult(success)
                        },
                    )
                },
                globalFocusManager = globalFocusManager,
                keyboardController = keyboardController,
                onFocusChange = { isFocused ->
                    if (isFocused) {
                        currentlyFocusedItemId = -1 // Special ID for new tag item
                    }
                },
            )

            val hapticFeedback = LocalHapticFeedback.current
            val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
                // Clear focus during reordering
                globalFocusManager.clearFocus()
                keyboardController?.hide()
                currentlyFocusedItemId = null

                viewModel.onEvent(TagListEvent.ReorderTagsLive(from.index, to.index))
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(state.listTags, key = { _, item -> item.id }) { index, item ->
                    ReorderableItem(reorderableLazyListState, key = item.id) { isDragging ->
                        if (index > 0) EnclyGroupDivider()
                        TagEditItem(
                            shape = groupRowShape(index, state.listTags.size),
                            item = item,
                            onAction = { action, tag ->
                                when (action) {
                                    TagEditTypeAction.EDIT -> {
                                        viewModel.onEvent(TagListEvent.UpdateTag(tag))
                                        currentlyFocusedItemId = null
                                    }

                                    TagEditTypeAction.VISIBLE -> {
                                        viewModel.onEvent(TagListEvent.ToggleVisibleTag(tag))
                                    }

                                    TagEditTypeAction.DELETE -> {
                                        globalFocusManager.clearFocus()
                                        keyboardController?.hide()
                                        currentlyFocusedItemId = null
                                        tagDelete = tag
                                        showDialog = true
                                    }
                                }
                            },
                            isDragging = isDragging,
                            focus = TagFocus(
                                focusManager = globalFocusManager,
                                keyboardController = keyboardController,
                                onFocusChange = { isFocused ->
                                    if (isFocused) currentlyFocusedItemId = item.id
                                },
                                preventFocus = currentlyFocusedItemId != null && currentlyFocusedItemId != item.id,
                            ),
                        )
                    }
                }
            }
        }

        RequestDeleteTagsDialog(
            showDialog = showDialog,
            onDismiss = { showDialog = false },
            onConfirm = {
                viewModel.onEvent(TagListEvent.DeleteTag(tagDelete))
                tagDelete = Tag()
                showDialog = false
            },
        )
    }
}

@Suppress("LongMethod") // Existing Material text-field layout; persistence result handling is local.
@Composable
private fun NewTagItem(
    item: String,
    onSave: (Tag, (Boolean) -> Unit) -> Unit,
    globalFocusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onFocusChange: (Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(item)) }
    val focusRequester = remember { FocusRequester() }
    var isTextFieldEnabled by remember { mutableStateOf(false) }
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)

    // Handle focus changes
    LaunchedEffect(isTextFieldEnabled) {
        currentOnFocusChange(isTextFieldEnabled)
    }

    fun handleSave() {
        if (text.text.isBlank()) {
            text = TextFieldValue("")
            isTextFieldEnabled = false
            globalFocusManager.clearFocus()
            keyboardController?.hide()
            return
        }

        onSave(Tag(nameTag = text.text.trim())) { success ->
            if (success) {
                text = TextFieldValue("")
                isTextFieldEnabled = false
                globalFocusManager.clearFocus()
                keyboardController?.hide()
            }
        }
    }

    fun handleCancel() {
        text = TextFieldValue("")
        isTextFieldEnabled = false
        globalFocusManager.clearFocus()
        keyboardController?.hide()
    }

    // A typed but unsaved tag name is persisted before the background re-lock drops this
    // screen.
    SaveOnPause { if (text.text.isNotBlank()) handleSave() }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isTextFieldEnabled) {
            BorderStroke(EnclyTheme.spacing.stroke, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        TextField(
            value = text,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { handleSave() }),
            visualTransformation = VisualTransformation.None,
            onValueChange = { newValue ->
                val filteredText = newValue.text.take(TAG_NAME_MAX_LENGTH)
                text = newValue.copy(text = filteredText)
            },
            leadingIcon = {
                IconButton(onClick = {
                    if (isTextFieldEnabled) {
                        handleCancel()
                    } else {
                        isTextFieldEnabled = true
                    }
                }) {
                    if (isTextFieldEnabled) {
                        Icon(
                            Lucide.X,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Icon(
                            Lucide.Plus,
                            contentDescription = stringResource(R.string.create_new_tag),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isTextFieldEnabled = focusState.isFocused
                },
            colors = transparentFieldColors(),
            maxLines = 1,
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge,
            label = { Text(stringResource(R.string.create_new_tag), style = MaterialTheme.typography.bodyMedium) },
            trailingIcon = {
                if (isTextFieldEnabled) {
                    IconButton(onClick = { handleSave() }) {
                        Icon(
                            Lucide.Check,
                            contentDescription = stringResource(R.string.save),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )

        if (isTextFieldEnabled) {
            LaunchedEffect(isTextFieldEnabled) {
                text = text.copy(selection = TextRange(text.text.length))
                focusRequester.requestFocus()
            }
        }
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

/** A text field that draws nothing of its own: the row around it is the container. */
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
)

/** How a tag row shares the one keyboard focus of the list. */
private class TagFocus(
    val focusManager: FocusManager,
    val keyboardController: SoftwareKeyboardController?,
    val onFocusChange: (Boolean) -> Unit,
    /** Another row is being edited: this one must not take the focus. */
    val preventFocus: Boolean,
) {
    fun release() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
}

@Composable
private fun TagEditItem(
    shape: Shape,
    item: Tag,
    onAction: (TagEditTypeAction, Tag) -> Unit,
    focus: TagFocus,
    isDragging: Boolean = false,
) {
    var text by remember { mutableStateOf(TextFieldValue(item.nameTag)) }
    val focusRequester = remember { FocusRequester() }
    var isTextFieldEnabled by remember { mutableStateOf(false) }
    val currentOnFocusChange by rememberUpdatedState(focus.onFocusChange)

    // Sync text with item changes
    LaunchedEffect(item.nameTag) { text = TextFieldValue(item.nameTag) }

    // Handle focus changes
    LaunchedEffect(isTextFieldEnabled) { currentOnFocusChange(isTextFieldEnabled) }

    // Prevent focus if another item is focused
    LaunchedEffect(focus.preventFocus) {
        if (focus.preventFocus && isTextFieldEnabled) {
            isTextFieldEnabled = false
            focus.release()
        }
    }

    fun handleSave() {
        if (text.text.isNotBlank()) onAction(TagEditTypeAction.EDIT, item.copy(nameTag = text.text.trim()))
        isTextFieldEnabled = false
        focus.release()
    }

    SaveOnPause {
        if (isTextFieldEnabled && text.text.isNotBlank() && text.text.trim() != item.nameTag) handleSave()
    }

    Surface(
        shape = shape,
        color = if (isDragging || isTextFieldEnabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        if (isTextFieldEnabled) {
            TagNameField(
                text = text,
                onTextChange = { newValue ->
                    text = newValue.copy(text = newValue.text.replace(" ", "").take(TAG_NAME_MAX_LENGTH))
                },
                actions = TagFieldActions(
                    onSave = ::handleSave,
                    onDelete = {
                        isTextFieldEnabled = false
                        focus.release()
                        onAction(TagEditTypeAction.DELETE, item)
                    },
                    onFocusChanged = { isTextFieldEnabled = it },
                ),
                focusRequester = focusRequester,
            )
        } else {
            TagRow(
                item = item,
                onToggleVisible = { onAction(TagEditTypeAction.VISIBLE, item) },
                onEdit = { if (!focus.preventFocus) isTextFieldEnabled = true },
            )
        }

        if (isTextFieldEnabled && !focus.preventFocus) {
            LaunchedEffect(isTextFieldEnabled) {
                // Start from the stored name: a refused rename (a taken name) is not kept.
                text = TextFieldValue(item.nameTag, TextRange(item.nameTag.length))
                focusRequester.requestFocus()
            }
        }
    }
}

/** What the rename field reports: save, delete, and whether it holds the focus. */
private class TagFieldActions(val onSave: () -> Unit, val onDelete: () -> Unit, val onFocusChanged: (Boolean) -> Unit)

/** A tag being renamed: delete at the start, save at the end. */
@Composable
private fun TagNameField(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    actions: TagFieldActions,
    focusRequester: FocusRequester,
) {
    TextField(
        value = text,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
        visualTransformation = VisualTransformation.None,
        onValueChange = onTextChange,
        leadingIcon = {
            IconButton(onClick = actions.onDelete) {
                Icon(
                    Lucide.Trash2,
                    contentDescription = stringResource(R.string.delete_tag),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { actions.onFocusChanged(it.isFocused) },
        colors = transparentFieldColors(),
        maxLines = 1,
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge,
        trailingIcon = {
            IconButton(onClick = actions.onSave) {
                Icon(
                    Lucide.Check,
                    contentDescription = stringResource(R.string.save),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/** A tag at rest: its name, and buttons to hide or show it and to rename it. */
@Composable
private fun TagRow(item: Tag, onToggleVisible: () -> Unit, onEdit: () -> Unit) {
    EnclyListRow(
        title = item.nameTag,
        icon = Lucide.TagIcon,
        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
    ) {
        IconButton(onClick = onToggleVisible) {
            Icon(
                if (item.isVisible) Lucide.Eye else Lucide.EyeOff,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(if (item.isVisible) R.string.tag_hide else R.string.tag_show),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Lucide.Pencil,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.tag_edit),
            )
        }
    }
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
